"""Gmail client for fetching threads and messages."""

import json
from pathlib import Path

from florian.gmail.auth import GmailAuth


class GmailClient:
    """Client for interacting with Gmail API to fetch threads and messages."""

    def __init__(self, auth: GmailAuth | None = None):
        """Initialize Gmail client.

        Parameters
        ----------
        auth : GmailAuth, optional
            Authentication instance, creates new one if not provided
        """
        self.auth = auth or GmailAuth()
        self.service = self.auth.get_service()

    def list_threads(self, query: str = "", max_results: int = 100):
        """List Gmail threads matching query.

        Parameters
        ----------
        query : str
            Gmail search query (e.g., "is:unread", "from:user@example.com")
        max_results : int
            Maximum number of threads to return

        Returns
        -------
        list
            List of thread objects
        """
        threads: list[dict] = []
        page_token = None

        try:
            while len(threads) < max_results:
                # Calculate how many results to request in this batch
                batch_size = min(100, max_results - len(threads))

                # Call the Gmail API
                if page_token:
                    results = (
                        self.service.users()
                        .threads()
                        .list(
                            userId="me",
                            q=query,
                            maxResults=batch_size,
                            pageToken=page_token,
                        )
                        .execute()
                    )
                else:
                    results = (
                        self.service.users()
                        .threads()
                        .list(userId="me", q=query, maxResults=batch_size)
                        .execute()
                    )

                thread_list = results.get("threads", [])
                threads.extend(thread_list)

                # Check if there are more pages
                page_token = results.get("nextPageToken")
                if not page_token:
                    break

            return threads[:max_results]

        except Exception as error:
            print(f"An error occurred: {error}")
            return []

    def get_thread(self, thread_id: str, format: str = "full"):
        """Get a specific Gmail thread with all messages.

        Parameters
        ----------
        thread_id : str
            Thread ID to fetch
        format : str
            Format for message bodies ("full", "metadata", "minimal")

        Returns
        -------
        dict
            Thread object with messages
        """
        try:
            thread = (
                self.service.users()
                .threads()
                .get(userId="me", id=thread_id, format=format)
                .execute()
            )
            return thread
        except Exception as error:
            print(f"An error occurred fetching thread {thread_id}: {error}")
            return None

    def get_message(self, message_id: str, format: str = "full"):
        """Get a specific Gmail message.

        Parameters
        ----------
        message_id : str
            Message ID to fetch
        format : str
            Format for message body ("full", "metadata", "minimal", "raw")

        Returns
        -------
        dict
            Message object
        """
        try:
            message = (
                self.service.users()
                .messages()
                .get(userId="me", id=message_id, format=format)
                .execute()
            )
            return message
        except Exception as error:
            print(f"An error occurred fetching message {message_id}: {error}")
            return None

    def parse_message_parts(self, parts, result=None):
        """Recursively parse message parts to extract text content.

        Parameters
        ----------
        parts : list
            List of message parts
        result : dict, optional
            Dictionary to store parsed content

        Returns
        -------
        dict
            Parsed message content with text/plain and text/html
        """
        if result is None:
            result = {"text": "", "html": ""}

        for part in parts:
            mime_type = part.get("mimeType", "")
            body = part.get("body", {})
            data = body.get("data", "")

            if mime_type == "text/plain" and data:
                import base64

                text = base64.urlsafe_b64decode(data).decode("utf-8", errors="ignore")
                result["text"] += text
            elif mime_type == "text/html" and data:
                import base64

                html = base64.urlsafe_b64decode(data).decode("utf-8", errors="ignore")
                result["html"] += html

            # Handle multipart messages
            if "parts" in part:
                self.parse_message_parts(part["parts"], result)

        return result

    def extract_thread_content(self, thread):
        """Extract readable content from a thread.

        Parameters
        ----------
        thread : dict
            Thread object from Gmail API

        Returns
        -------
        dict
            Extracted thread content with messages
        """
        extracted = {
            "id": thread.get("id"),
            "historyId": thread.get("historyId"),
            "messages": [],
        }

        for message in thread.get("messages", []):
            msg_data = {
                "id": message.get("id"),
                "threadId": message.get("threadId"),
                "labelIds": message.get("labelIds", []),
                "snippet": message.get("snippet", ""),
                "headers": {},
                "body": {"text": "", "html": ""},
            }

            # Extract headers
            headers = message.get("payload", {}).get("headers", [])
            for header in headers:
                name = header.get("name", "").lower()
                if name in ["from", "to", "subject", "date", "cc", "bcc"]:
                    msg_data["headers"][name] = header.get("value", "")

            # Extract body
            payload = message.get("payload", {})
            if "parts" in payload:
                msg_data["body"] = self.parse_message_parts(payload["parts"])
            else:
                # Single part message
                body = payload.get("body", {})
                data = body.get("data", "")
                if data:
                    import base64

                    content = base64.urlsafe_b64decode(data).decode("utf-8", errors="ignore")
                    mime_type = payload.get("mimeType", "")
                    if "text/plain" in mime_type:
                        msg_data["body"]["text"] = content
                    elif "text/html" in mime_type:
                        msg_data["body"]["html"] = content

            extracted["messages"].append(msg_data)

        return extracted

    def search_threads(
        self,
        query: str = "from:me",
        max_results: int = 500,
        save_to_file: str | None = None,
        fetch_metadata: bool = True,
    ):
        """Search for thread IDs matching a query.

        Parameters
        ----------
        query : str
            Gmail search query (default: "from:me" for threads with your replies)
        max_results : int
            Maximum number of thread IDs to return
        save_to_file : str, optional
            Path to save thread IDs as JSON
        fetch_metadata : bool
            Whether to fetch thread metadata for subjects (slower but more informative)

        Returns
        -------
        list
            List of thread IDs and basic metadata
        """
        print(f"Searching for threads with query: {query}")
        threads = self.list_threads(query=query, max_results=max_results)

        if not threads:
            print("No threads found.")
            return []

        print(f"Found {len(threads)} threads matching your search.")

        # Extract thread info with subjects if requested
        thread_ids = []
        for i, thread in enumerate(threads):
            thread_info = {
                "id": thread["id"],
                "snippet": thread.get("snippet", "")[:100],  # First 100 chars of snippet
            }

            # Optionally fetch the thread metadata to get subject
            if fetch_metadata:
                if i % 10 == 0:  # Progress indicator
                    print(f"Fetching metadata... {i}/{len(threads)}")
                try:
                    thread_data = self.get_thread(thread["id"], format="metadata")
                    if (
                        thread_data
                        and "messages" in thread_data
                        and len(thread_data["messages"]) > 0
                    ):
                        # Get subject from first message
                        first_msg = thread_data["messages"][0]
                        headers = first_msg.get("payload", {}).get("headers", [])
                        for header in headers:
                            if header.get("name", "").lower() == "subject":
                                thread_info["subject"] = header.get("value", "No subject")
                                break
                        if "subject" not in thread_info:
                            thread_info["subject"] = "No subject"
                except Exception:
                    thread_info["subject"] = "Error fetching subject"

            thread_ids.append(thread_info)

        if save_to_file:
            Path(save_to_file).parent.mkdir(parents=True, exist_ok=True)
            with open(save_to_file, "w", encoding="utf-8") as f:
                json.dump(thread_ids, f, indent=2, ensure_ascii=False)
            print(f"Saved {len(thread_ids)} thread IDs to {save_to_file}")

        return thread_ids

    def fetch_threads(
        self, query: str = "", max_threads: int = 10, save_to_file: str | None = None
    ):
        """Fetch multiple threads with their messages.

        Parameters
        ----------
        query : str
            Gmail search query
        max_threads : int
            Maximum number of threads to fetch
        save_to_file : str, optional
            Path to save fetched threads as JSON

        Returns
        -------
        list
            List of extracted thread contents
        """
        print(f"Fetching up to {max_threads} threads...")
        thread_list = self.list_threads(query=query, max_results=max_threads)

        if not thread_list:
            print("No threads found.")
            return []

        print(f"Found {len(thread_list)} threads. Fetching details...")
        threads_data = []

        for i, thread_item in enumerate(thread_list):
            print(f"Fetching thread {i + 1}/{len(thread_list)}: {thread_item['id']}")
            thread = self.get_thread(thread_item["id"])
            if thread:
                extracted = self.extract_thread_content(thread)
                threads_data.append(extracted)

        if save_to_file:
            Path(save_to_file).parent.mkdir(parents=True, exist_ok=True)
            with open(save_to_file, "w", encoding="utf-8") as f:
                json.dump(threads_data, f, indent=2, ensure_ascii=False)
            print(f"Saved {len(threads_data)} threads to {save_to_file}")

        return threads_data

    def fetch_thread_list(self, thread_ids: list, save_to_file: str | None = None):
        """Fetch full content for a list of thread IDs.

        Parameters
        ----------
        thread_ids : list
            List of thread IDs or thread info dicts with 'id' key
        save_to_file : str, optional
            Path to save fetched threads as JSON

        Returns
        -------
        list
            List of extracted thread contents
        """
        if not thread_ids:
            print("No thread IDs provided.")
            return []

        # Handle both list of strings and list of dicts
        ids_to_fetch = []
        for item in thread_ids:
            if isinstance(item, str):
                ids_to_fetch.append(item)
            elif isinstance(item, dict) and "id" in item:
                ids_to_fetch.append(item["id"])

        print(f"Fetching {len(ids_to_fetch)} threads...")
        threads_data = []

        for i, thread_id in enumerate(ids_to_fetch):
            print(f"Fetching thread {i + 1}/{len(ids_to_fetch)}: {thread_id}")
            thread = self.get_thread(thread_id)
            if thread:
                extracted = self.extract_thread_content(thread)
                threads_data.append(extracted)

        if save_to_file:
            Path(save_to_file).parent.mkdir(parents=True, exist_ok=True)
            with open(save_to_file, "w", encoding="utf-8") as f:
                json.dump(threads_data, f, indent=2, ensure_ascii=False)
            print(f"Saved {len(threads_data)} threads to {save_to_file}")

        return threads_data
