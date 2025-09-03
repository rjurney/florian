"""OpenSearch client for indexing and searching email threads."""

import json
from pathlib import Path

from opensearchpy import OpenSearch, helpers
from opensearchpy.exceptions import RequestError


class OpenSearchClient:
    """Client for interacting with OpenSearch to index and search email threads."""

    def __init__(
        self,
        host: str = "localhost",
        port: int = 9200,
        use_ssl: bool = False,
        embedding_dim: int = 1536,
    ):
        """Initialize OpenSearch client.

        Parameters
        ----------
        host : str
            OpenSearch host
        port : int
            OpenSearch port
        use_ssl : bool
            Whether to use SSL/TLS
        """
        self.client = OpenSearch(
            hosts=[{"host": host, "port": port}],
            http_compress=True,
            use_ssl=use_ssl,
            verify_certs=False,
            ssl_show_warn=False,
        )
        self.index_name = "gmail-threads"
        self.embedding_dim = embedding_dim

    def create_index(self, recreate: bool = False):
        """Create the Gmail threads index with appropriate mappings.

        Parameters
        ----------
        recreate : bool
            Whether to delete and recreate the index if it exists

        Returns
        -------
        bool
            True if index was created or already exists
        """
        # Index settings and mappings optimized for email search
        index_body = {
            "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0,
                "index.knn": True,
                "index.knn.space_type": "cosinesimil",
                "analysis": {
                    "analyzer": {
                        "email_analyzer": {
                            "type": "custom",
                            "tokenizer": "uax_url_email",
                            "filter": ["lowercase", "stop"],
                        }
                    }
                },
            },
            "mappings": {
                "properties": {
                    "id": {"type": "keyword"},
                    "thread_id": {"type": "keyword"},
                    "message_id": {"type": "keyword"},
                    "subject": {
                        "type": "text",
                        "fields": {"keyword": {"type": "keyword", "ignore_above": 256}},
                    },
                    "from": {
                        "type": "text",
                        "analyzer": "email_analyzer",
                        "fields": {"keyword": {"type": "keyword"}},
                    },
                    "to": {
                        "type": "text",
                        "analyzer": "email_analyzer",
                        "fields": {"keyword": {"type": "keyword"}},
                    },
                    "cc": {
                        "type": "text",
                        "analyzer": "email_analyzer",
                        "fields": {"keyword": {"type": "keyword"}},
                    },
                    "bcc": {
                        "type": "text",
                        "analyzer": "email_analyzer",
                        "fields": {"keyword": {"type": "keyword"}},
                    },
                    "date": {
                        "type": "date",
                        "null_value": None,
                        "ignore_malformed": True,
                    },
                    "timestamp": {
                        "type": "date",
                        "null_value": None,
                        "ignore_malformed": True,
                    },
                    "body_text": {"type": "text", "analyzer": "standard"},
                    "body_html": {"type": "text", "analyzer": "standard"},
                    "snippet": {"type": "text"},
                    "labels": {"type": "keyword"},
                    "thread_message_count": {"type": "integer"},
                    "thread_position": {"type": "integer"},
                    "subject_embedding": {
                        "type": "knn_vector",
                        "dimension": self.embedding_dim,
                        "method": {
                            "name": "hnsw",
                            "space_type": "cosinesimil",
                            "engine": "nmslib",
                            "parameters": {"ef_construction": 128, "m": 24},
                        },
                    },
                    "body_embedding": {
                        "type": "knn_vector",
                        "dimension": self.embedding_dim,
                        "method": {
                            "name": "hnsw",
                            "space_type": "cosinesimil",
                            "engine": "nmslib",
                            "parameters": {"ef_construction": 128, "m": 24},
                        },
                    },
                }
            },
        }

        try:
            # Check if index exists
            if self.client.indices.exists(index=self.index_name):
                if recreate:
                    print(f"Deleting existing index: {self.index_name}")
                    self.client.indices.delete(index=self.index_name)
                    print(f"Creating new index: {self.index_name}")
                    response = self.client.indices.create(
                        index=self.index_name, body=index_body
                    )
                else:
                    print(f"Index {self.index_name} already exists")
                    return True
            else:
                print(f"Creating index: {self.index_name}")
                response = self.client.indices.create(
                    index=self.index_name, body=index_body
                )

            return response.get("acknowledged", False)

        except RequestError as e:
            if "resource_already_exists_exception" in str(e):
                print(f"Index {self.index_name} already exists")
                return True
            else:
                print(f"Error creating index: {e}")
                raise

    def index_threads(self, threads_file: str = "data/gmail_threads.json"):
        """Index Gmail threads from a JSON file.

        Parameters
        ----------
        threads_file : str
            Path to JSON file containing threads

        Returns
        -------
        tuple
            (success_count, error_count)
        """
        threads_path = Path(threads_file)
        if not threads_path.exists():
            print(f"File not found: {threads_file}")
            return 0, 0

        print(f"Loading threads from {threads_file}...")
        with open(threads_path, "r", encoding="utf-8") as f:
            threads = json.load(f)

        if not threads:
            print("No threads to index")
            return 0, 0

        # Prepare documents for bulk indexing
        documents = []
        for thread in threads:
            thread_id = thread.get("id")
            messages = thread.get("messages", [])
            message_count = len(messages)

            for position, message in enumerate(messages):
                # Parse date to ISO format
                date_str = message.get("headers", {}).get("date", "")
                iso_date = None
                if date_str:
                    try:
                        # Try to parse the date
                        from email.utils import parsedate_to_datetime

                        dt = parsedate_to_datetime(date_str)
                        iso_date = dt.isoformat()
                    except Exception:
                        iso_date = None

                # Build document
                doc_source = {
                    "id": message.get("id"),
                    "thread_id": thread_id,
                    "message_id": message.get("id"),
                    "subject": message.get("headers", {}).get("subject", ""),
                    "from": message.get("headers", {}).get("from", ""),
                    "to": message.get("headers", {}).get("to", ""),
                    "cc": message.get("headers", {}).get("cc", ""),
                    "bcc": message.get("headers", {}).get("bcc", ""),
                    "date": iso_date if iso_date else None,
                    "timestamp": iso_date,
                    "body_text": message.get("body", {}).get("text", ""),
                    "body_html": message.get("body", {}).get("html", ""),
                    "snippet": message.get("snippet", ""),
                    "labels": message.get("labelIds", []),
                    "thread_message_count": message_count,
                    "thread_position": position,
                }

                # Add embeddings if present
                if "embeddings" in message:
                    if "subject_embedding" in message["embeddings"]:
                        doc_source["subject_embedding"] = message["embeddings"][
                            "subject_embedding"
                        ]
                    if "body_embedding" in message["embeddings"]:
                        doc_source["body_embedding"] = message["embeddings"][
                            "body_embedding"
                        ]

                doc = {
                    "_index": self.index_name,
                    "_id": message.get("id"),
                    "_source": doc_source,
                }
                documents.append(doc)

        print(f"Indexing {len(documents)} messages from {len(threads)} threads...")

        # Bulk index documents
        try:
            success, errors = helpers.bulk(
                self.client, documents, raise_on_error=False, raise_on_exception=False
            )

            print(f"✓ Successfully indexed {success} messages")
            if errors:
                print(f"✗ Failed to index {len(errors)} messages")
                for error in errors[:5]:  # Show first 5 errors
                    print(f"  Error: {error}")

            return success, len(errors) if errors else 0

        except Exception as e:
            print(f"Error during bulk indexing: {e}")
            return 0, len(documents)

    def search(self, query: str, size: int = 10):
        """Search for email messages.

        Parameters
        ----------
        query : str
            Search query
        size : int
            Maximum number of results

        Returns
        -------
        dict
            Search results
        """
        search_body = {
            "query": {
                "multi_match": {
                    "query": query,
                    "fields": ["subject^2", "body_text", "from", "to", "snippet"],
                }
            },
            "size": size,
            "sort": [{"timestamp": {"order": "desc", "missing": "_last"}}],
            "highlight": {"fields": {"body_text": {}, "subject": {}}},
        }

        try:
            response = self.client.search(index=self.index_name, body=search_body)
            return response
        except Exception as e:
            print(f"Search error: {e}")
            return None

    def knn_search(
        self,
        vector: list,
        field: str = "body_embedding",
        k: int = 10,
        min_score: float = 0.0,
    ):
        """Perform k-NN vector similarity search.

        Parameters
        ----------
        vector : list
            Query vector for similarity search
        field : str
            Vector field to search ('subject_embedding' or 'body_embedding')
        k : int
            Number of nearest neighbors to return
        min_score : float
            Minimum similarity score threshold

        Returns
        -------
        dict
            Search results with similarity scores
        """
        search_body = {
            "size": k,
            "query": {
                "knn": {
                    field: {
                        "vector": vector,
                        "k": k,
                    }
                }
            },
            "min_score": min_score,
            "_source": {
                "excludes": ["subject_embedding", "body_embedding", "body_html"]
            },
        }

        try:
            response = self.client.search(index=self.index_name, body=search_body)
            return response
        except Exception as e:
            print(f"kNN search error: {e}")
            return None

    def hybrid_search(
        self,
        query: str,
        vector: list,
        field: str = "body_embedding",
        k: int = 10,
        text_weight: float = 0.3,
        vector_weight: float = 0.7,
    ):
        """Perform hybrid text + vector search.

        Parameters
        ----------
        query : str
            Text search query
        vector : list
            Query vector for similarity search
        field : str
            Vector field to search
        k : int
            Number of results to return
        text_weight : float
            Weight for text search (0-1)
        vector_weight : float
            Weight for vector search (0-1)

        Returns
        -------
        dict
            Combined search results
        """
        search_body = {
            "size": k,
            "query": {
                "bool": {
                    "should": [
                        {
                            "multi_match": {
                                "query": query,
                                "fields": ["subject^2", "body_text", "from", "to"],
                                "boost": text_weight,
                            }
                        },
                        {
                            "knn": {
                                field: {
                                    "vector": vector,
                                    "k": k,
                                    "boost": vector_weight,
                                }
                            }
                        },
                    ]
                }
            },
            "_source": {
                "excludes": ["subject_embedding", "body_embedding", "body_html"]
            },
            "highlight": {"fields": {"body_text": {}, "subject": {}}},
        }

        try:
            response = self.client.search(index=self.index_name, body=search_body)
            return response
        except Exception as e:
            print(f"Hybrid search error: {e}")
            return None

    def get_stats(self):
        """Get index statistics.

        Returns
        -------
        dict
            Index statistics
        """
        try:
            # Get document count
            count_response = self.client.count(index=self.index_name)
            doc_count = count_response.get("count", 0)

            # Get index stats
            stats_response = self.client.indices.stats(index=self.index_name)
            index_stats = stats_response.get("indices", {}).get(self.index_name, {})

            # Get unique threads count
            thread_agg = {
                "aggs": {"unique_threads": {"cardinality": {"field": "thread_id"}}}
            }
            agg_response = self.client.search(
                index=self.index_name, body=thread_agg, size=0
            )
            unique_threads = (
                agg_response.get("aggregations", {})
                .get("unique_threads", {})
                .get("value", 0)
            )

            return {
                "total_messages": doc_count,
                "unique_threads": unique_threads,
                "index_size": index_stats.get("primaries", {})
                .get("store", {})
                .get("size_in_bytes", 0),
                "index_name": self.index_name,
            }

        except Exception as e:
            print(f"Error getting stats: {e}")
            return None

    def health_check(self):
        """Check if OpenSearch is accessible and healthy.

        Returns
        -------
        bool
            True if OpenSearch is healthy
        """
        try:
            health = self.client.cluster.health()
            status = health.get("status")
            print(f"OpenSearch cluster status: {status}")
            return status in ["green", "yellow"]
        except Exception as e:
            print(f"OpenSearch is not accessible: {e}")
            return False
