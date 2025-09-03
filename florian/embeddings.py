"""Email embeddings generation using sentence-transformers."""

import json
from pathlib import Path
from typing import cast

import numpy as np
import torch
from sentence_transformers import SentenceTransformer


class EmailEmbedder:
    """Generate embeddings for email content using sentence-transformers."""

    def __init__(self, model_name: str = "Qwen/Qwen3-Embedding-4B"):
        """Initialize the embedder with specified model.

        Parameters
        ----------
        model_name : str
            Name of the sentence-transformer model to use
        """
        # Detect device - use MPS for Mac, CUDA for NVIDIA, CPU otherwise
        if torch.backends.mps.is_available():
            self.device = "mps"
            print("Using MPS (Metal Performance Shaders) for acceleration")
        elif torch.cuda.is_available():
            self.device = "cuda"
            print(f"Using CUDA GPU: {torch.cuda.get_device_name(0)}")
        else:
            self.device = "cpu"
            print("Using CPU for embeddings")

        print(f"Loading model: {model_name}")
        # FP8 is not supported for model storage in PyTorch yet
        # Using bfloat16 for memory efficiency on MPS
        dtype = torch.bfloat16

        self.model = SentenceTransformer(
            model_name, device=self.device, model_kwargs={"torch_dtype": dtype}
        )

        # Convert model to bfloat16
        self.model = self.model.bfloat16()

        self.embedding_dim = self.model.get_sentence_embedding_dimension()
        print(f"Model loaded in BF16. Embedding dimension: {self.embedding_dim}")

    def embed_text(self, text: str | list[str]) -> np.ndarray:
        """Generate embeddings for text.

        Parameters
        ----------
        text : str or list of str
            Text or list of texts to embed

        Returns
        -------
        np.ndarray
            Embeddings array
        """
        if isinstance(text, str):
            text = [text]

        # Generate embeddings
        embeddings = self.model.encode(
            text, convert_to_numpy=True, show_progress_bar=len(text) > 10
        )

        return cast(np.ndarray, embeddings)

    def embed_email(self, message: dict) -> dict:
        """Generate embeddings for an email message.

        Parameters
        ----------
        message : dict
            Email message with headers and body

        Returns
        -------
        dict
            Dictionary with title and body embeddings
        """
        # Get subject for subject embedding
        subject = message.get("headers", {}).get("subject", "")
        if not subject:
            subject = "No Subject"

        # Get body text (prefer text over HTML)
        body_text = message.get("body", {}).get("text", "")
        if not body_text:
            # Fall back to snippet if no body text
            body_text = message.get("snippet", "")

        if not body_text:
            body_text = "Empty message"

        # Truncate very long texts (max ~8000 tokens, roughly 32000 chars)
        max_length = 32000
        if len(body_text) > max_length:
            body_text = body_text[:max_length] + "..."

        # Generate embeddings
        subject_embedding = self.embed_text(subject)[0]
        body_embedding = self.embed_text(body_text)[0]

        return {
            "subject_embedding": subject_embedding.tolist(),
            "body_embedding": body_embedding.tolist(),
        }

    def embed_threads(
        self,
        threads_file: str = "data/gmail_threads.json",
        output_file: str | None = None,
    ) -> list:
        """Generate embeddings for all messages in threads file using batch processing.

        Parameters
        ----------
        threads_file : str
            Path to JSON file containing threads
        output_file : str, optional
            Path to save embedded threads

        Returns
        -------
        list
            List of threads with embeddings added
        """
        threads_path = Path(threads_file)
        if not threads_path.exists():
            print(f"File not found: {threads_file}")
            return []

        print(f"Loading threads from {threads_file}...")
        with open(threads_path, "r", encoding="utf-8") as f:
            threads = json.load(f)

        if not threads:
            print("No threads to process")
            return []

        # Collect all subjects and bodies for batch processing
        all_subjects = []
        all_bodies = []
        message_indices = []  # Track which thread and message each embedding belongs to

        for thread_idx, thread in enumerate(threads):
            for msg_idx, message in enumerate(thread.get("messages", [])):
                # Get subject
                subject = message.get("headers", {}).get("subject", "")
                if not subject:
                    subject = "No Subject"

                # Get body text (prefer text over HTML)
                body_text = message.get("body", {}).get("text", "")
                if not body_text:
                    body_text = message.get("snippet", "")
                if not body_text:
                    body_text = "Empty message"

                # Truncate very long texts
                max_length = 32000
                if len(body_text) > max_length:
                    body_text = body_text[:max_length] + "..."

                all_subjects.append(subject)
                all_bodies.append(body_text)
                message_indices.append((thread_idx, msg_idx))

        total_messages = len(all_subjects)
        print(f"Processing {total_messages} messages from {len(threads)} threads...")

        # Batch encode all subjects at once
        print("Encoding all subjects on GPU...")
        subject_embeddings = self.model.encode(
            all_subjects,
            convert_to_numpy=True,
            show_progress_bar=True,
            batch_size=1,  # Process one at a time for Qwen3-4B to avoid memory issues
        )

        # Batch encode all bodies at once
        print("Encoding all bodies on GPU...")
        body_embeddings = self.model.encode(
            all_bodies,
            convert_to_numpy=True,
            show_progress_bar=True,
            batch_size=1,  # Process one at a time for longer texts with large model
        )

        # Map embeddings back to messages
        print("Mapping embeddings back to messages...")
        for idx, (thread_idx, msg_idx) in enumerate(message_indices):
            threads[thread_idx]["messages"][msg_idx]["embeddings"] = {
                "subject_embedding": subject_embeddings[idx].tolist(),
                "body_embedding": body_embeddings[idx].tolist(),
            }

        print(f"✓ Generated embeddings for {total_messages} messages")

        if output_file:
            output_path = Path(output_file)
            output_path.parent.mkdir(parents=True, exist_ok=True)
            with open(output_path, "w", encoding="utf-8") as f:
                json.dump(threads, f, indent=2, ensure_ascii=False)
            print(f"✓ Saved embedded threads to {output_file}")

        return cast(list, threads)

    def search_similar(
        self, query: str, embeddings_list: list, field: str = "body", top_k: int = 10
    ) -> list:
        """Search for similar messages using cosine similarity.

        Parameters
        ----------
        query : str
            Query text to search for
        embeddings_list : list
            List of messages with embeddings
        field : str
            Field to search in ('subject' or 'body')
        top_k : int
            Number of top results to return

        Returns
        -------
        list
            Top k similar messages with scores
        """
        # Generate query embedding
        query_embedding = self.embed_text(query)[0]

        # Calculate similarities
        similarities = []
        embedding_field = f"{field}_embedding"

        for msg in embeddings_list:
            if "embeddings" in msg and embedding_field in msg["embeddings"]:
                msg_embedding = np.array(msg["embeddings"][embedding_field])
                # Cosine similarity
                similarity = np.dot(query_embedding, msg_embedding) / (
                    np.linalg.norm(query_embedding) * np.linalg.norm(msg_embedding)
                )
                similarities.append((similarity, msg))

        # Sort by similarity score
        similarities.sort(key=lambda x: x[0], reverse=True)

        # Return top k results
        return similarities[:top_k]
