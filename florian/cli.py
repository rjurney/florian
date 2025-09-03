"""Command-line interface for Florian."""

import json
from pathlib import Path

import click

from florian.gmail.auth import GmailAuth
from florian.gmail.client import GmailClient
from florian.opensearch.client import OpenSearchClient


@click.group()
@click.version_option()
def cli():
    """Florian - Gmail thread indexing for RAG."""
    pass


@cli.group()
def gmail():
    """Gmail-related commands."""
    pass


@gmail.command()
@click.option(
    "--credentials",
    default="auth/credentials.json",
    help="Path to OAuth2 credentials file",
)
def auth(credentials):
    """Authenticate with Gmail API."""
    click.echo("Authenticating with Gmail API...")
    auth = GmailAuth(credentials_file=credentials)
    try:
        auth.authenticate()
        click.echo("✓ Successfully authenticated with Gmail API")
        click.echo("Token saved to auth/token.json")
    except FileNotFoundError as e:
        click.echo(f"✗ {e}", err=True)
        click.echo("\nTo get credentials:", err=True)
        click.echo("1. Go to https://console.cloud.google.com/", err=True)
        click.echo("2. Create a new project or select existing", err=True)
        click.echo("3. Enable Gmail API", err=True)
        click.echo("4. Create OAuth 2.0 credentials (Desktop type)", err=True)
        click.echo("5. Download and save as auth/credentials.json", err=True)
    except Exception as e:
        click.echo(f"✗ Authentication failed: {e}", err=True)


@gmail.command()
@click.option("--query", "-q", default="", help="Gmail search query")
@click.option("--max-threads", "-n", default=10, help="Maximum threads to fetch")
@click.option(
    "--output", "-o", default="data/gmail_threads.json", help="Output JSON file path"
)
def fetch(query, max_threads, output):
    """Fetch Gmail threads."""
    click.echo("Fetching Gmail threads...")
    if query:
        click.echo(f"Query: {query}")

    client = GmailClient()

    try:
        threads = client.fetch_threads(
            query=query, max_threads=max_threads, save_to_file=output
        )

        if not threads:
            click.echo("No threads found")
            return

        click.echo(f"\n✓ Fetched {len(threads)} threads")

        # Display summary
        for thread in threads[:5]:  # Show first 5
            msg_count = len(thread.get("messages", []))
            first_msg = thread.get("messages", [{}])[0]
            subject = first_msg.get("headers", {}).get("subject", "No subject")
            from_addr = first_msg.get("headers", {}).get("from", "Unknown")

            click.echo(f"\n• Thread ID: {thread['id']}")
            click.echo(f"  Messages: {msg_count}")
            click.echo(f"  Subject: {subject[:60]}...")
            click.echo(f"  From: {from_addr}")

        if len(threads) > 5:
            click.echo(f"\n... and {len(threads) - 5} more threads")

    except Exception as e:
        click.echo(f"✗ Error fetching threads: {e}", err=True)


@gmail.command()
@click.argument("thread_id")
@click.option(
    "--output",
    "-o",
    default=None,
    help="Output JSON file path (default: data/thread_<id>.json)",
)
def thread(thread_id, output):
    """Fetch a specific Gmail thread."""
    click.echo(f"Fetching thread {thread_id}...")

    client = GmailClient()

    try:
        thread_data = client.get_thread(thread_id)
        if not thread_data:
            click.echo(f"Thread {thread_id} not found", err=True)
            return

        extracted = client.extract_thread_content(thread_data)

        # Use default output path if not specified
        if not output:
            output = f"data/thread_{thread_id}.json"

        Path(output).parent.mkdir(parents=True, exist_ok=True)
        with open(output, "w", encoding="utf-8") as f:
            json.dump(extracted, f, indent=2, ensure_ascii=False)
        click.echo(f"✓ Saved thread to {output}")

        # Display summary
        msg_count = len(extracted.get("messages", []))
        first_msg = extracted.get("messages", [{}])[0]
        subject = first_msg.get("headers", {}).get("subject", "No subject")

        click.echo(f"\n✓ Thread ID: {thread_id}")
        click.echo(f"  Messages: {msg_count}")
        click.echo(f"  Subject: {subject}")

        for i, msg in enumerate(extracted.get("messages", [])[:3]):
            click.echo(f"\n  Message {i + 1}:")
            click.echo(f"    From: {msg.get('headers', {}).get('from', 'Unknown')}")
            click.echo(f"    Date: {msg.get('headers', {}).get('date', 'Unknown')}")
            snippet = msg.get("snippet", "")[:100]
            if snippet:
                click.echo(f"    Preview: {snippet}...")

    except Exception as e:
        click.echo(f"✗ Error fetching thread: {e}", err=True)


@gmail.command()
@click.option(
    "--query", "-q", default="from:me", help="Gmail search query (default: from:me)"
)
@click.option("--max-results", "-n", default=500, help="Maximum threads to search")
@click.option(
    "--output", "-o", default="data/thread_ids.json", help="Output file for thread IDs"
)
@click.option(
    "--no-metadata", is_flag=True, help="Skip fetching message subjects (faster)"
)
def search(query, max_results, output, no_metadata):
    """Search for Gmail threads you've participated in."""
    click.echo(f"Searching Gmail threads with query: {query}")

    client = GmailClient()

    try:
        thread_ids = client.search_threads(
            query=query,
            max_results=max_results,
            save_to_file=output,
            fetch_metadata=not no_metadata,
        )

        if not thread_ids:
            click.echo("No threads found")
            return

        click.echo(f"\n✓ Found {len(thread_ids)} threads")
        click.echo(f"✓ Thread IDs saved to {output}")

        # Show first 10 threads with subjects
        for i, thread_info in enumerate(thread_ids[:10]):
            click.echo(f"\n{i + 1}. Thread ID: {thread_info['id']}")
            if thread_info.get("subject"):
                click.echo(f"   Subject: {thread_info['subject']}")
            elif thread_info.get("snippet"):
                click.echo(f"   Preview: {thread_info['snippet']}...")

        if len(thread_ids) > 10:
            click.echo(f"\n... and {len(thread_ids) - 10} more threads")
            click.echo("\nUse 'flo gmail fetch-searched' to download these threads")

    except Exception as e:
        click.echo(f"✗ Error searching threads: {e}", err=True)


@gmail.command()
@click.option(
    "--input", "-i", default="data/thread_ids.json", help="Input file with thread IDs"
)
@click.option(
    "--output",
    "-o",
    default="data/searched_threads.json",
    help="Output file for thread content",
)
@click.option(
    "--limit", "-l", default=None, type=int, help="Limit number of threads to fetch"
)
def fetch_searched(input, output, limit):
    """Fetch full content for previously searched threads."""
    click.echo(f"Loading thread IDs from {input}...")

    try:
        with open(input, "r") as f:
            thread_ids = json.load(f)

        if not thread_ids:
            click.echo("No thread IDs found in input file", err=True)
            return

        # Apply limit if specified
        if limit:
            thread_ids = thread_ids[:limit]
            click.echo(f"Limiting to first {limit} threads")

        click.echo(f"Fetching {len(thread_ids)} threads...")

        client = GmailClient()
        threads = client.fetch_thread_list(thread_ids=thread_ids, save_to_file=output)

        if threads:
            click.echo(f"\n✓ Fetched {len(threads)} threads")
            click.echo(f"✓ Saved to {output}")

            # Show summary
            total_messages = sum(len(t.get("messages", [])) for t in threads)
            click.echo(f"✓ Total messages: {total_messages}")
        else:
            click.echo("No threads could be fetched", err=True)

    except FileNotFoundError:
        click.echo(f"✗ Input file not found: {input}", err=True)
        click.echo("Run 'flo gmail search' first to create thread IDs file", err=True)
    except json.JSONDecodeError:
        click.echo(f"✗ Invalid JSON in {input}", err=True)
    except Exception as e:
        click.echo(f"✗ Error fetching threads: {e}", err=True)


@gmail.command()
@click.option("--max-results", "-n", default=50, help="Maximum threads to list")
@click.option("--query", "-q", default="", help="Gmail search query")
def list(max_results, query):
    """List Gmail threads."""
    click.echo("Listing Gmail threads...")
    if query:
        click.echo(f"Query: {query}")

    client = GmailClient()

    try:
        threads = client.list_threads(query=query, max_results=max_results)

        if not threads:
            click.echo("No threads found")
            return

        click.echo(f"\n✓ Found {len(threads)} threads\n")

        for i, thread in enumerate(threads[:20]):  # Show first 20
            click.echo(f"{i + 1}. Thread ID: {thread['id']}")
            if "snippet" in thread:
                snippet = thread["snippet"][:80]
                click.echo(f"   {snippet}...")

        if len(threads) > 20:
            click.echo(f"\n... and {len(threads) - 20} more threads")
            click.echo("\nUse 'flo gmail fetch' to download full thread content")

    except Exception as e:
        click.echo(f"✗ Error listing threads: {e}", err=True)


@cli.group()
def opensearch():
    """OpenSearch-related commands."""
    pass


@opensearch.command()
@click.option("--start", is_flag=True, help="Start OpenSearch containers")
@click.option("--stop", is_flag=True, help="Stop OpenSearch containers")
@click.option("--status", is_flag=True, help="Check OpenSearch status")
def setup(start, stop, status):
    """Setup and manage OpenSearch with Docker Compose."""
    import subprocess

    if start:
        click.echo("Starting OpenSearch containers...")
        try:
            result = subprocess.run(
                ["docker", "compose", "up", "-d"],
                capture_output=True,
                text=True,
                check=True,
            )
            click.echo("✓ OpenSearch containers started")
            click.echo("  OpenSearch: http://localhost:9200")
            click.echo("  OpenSearch Dashboards: http://localhost:5601")
            click.echo("\nWaiting for OpenSearch to be ready...")

            # Wait a bit for OpenSearch to start
            import time

            time.sleep(5)

            # Check health
            client = OpenSearchClient()
            if client.health_check():
                click.echo("✓ OpenSearch is healthy and ready")
            else:
                click.echo("⚠ OpenSearch is starting up, please wait a moment...")

        except subprocess.CalledProcessError as e:
            click.echo(f"✗ Failed to start OpenSearch: {e.stderr}", err=True)
        except Exception as e:
            click.echo(f"✗ Error: {e}", err=True)

    elif stop:
        click.echo("Stopping OpenSearch containers...")
        try:
            result = subprocess.run(
                ["docker", "compose", "down"],
                capture_output=True,
                text=True,
                check=True,
            )
            click.echo("✓ OpenSearch containers stopped")
        except subprocess.CalledProcessError as e:
            click.echo(f"✗ Failed to stop OpenSearch: {e.stderr}", err=True)

    elif status:
        click.echo("Checking OpenSearch status...")
        try:
            # Check if containers are running
            result = subprocess.run(
                ["docker", "compose", "ps"], capture_output=True, text=True, check=True
            )
            click.echo(result.stdout)

            # Check OpenSearch health
            client = OpenSearchClient()
            if client.health_check():
                stats = client.get_stats()
                if stats:
                    click.echo("\n✓ OpenSearch is healthy")
                    click.echo(f"  Index: {stats['index_name']}")
                    click.echo(f"  Total messages: {stats['total_messages']}")
                    click.echo(f"  Unique threads: {stats['unique_threads']}")
            else:
                click.echo("\n✗ OpenSearch is not accessible")

        except subprocess.CalledProcessError as e:
            click.echo(f"✗ Error checking status: {e.stderr}", err=True)

    else:
        click.echo(
            "Use --start to start OpenSearch, --stop to stop it, or --status to check"
        )


@opensearch.command()
@click.option(
    "--input",
    "-i",
    default="data/gmail_threads.json",
    help="Input JSON file with threads",
)
@click.option("--recreate", is_flag=True, help="Recreate index (delete existing data)")
def index(input, recreate):
    """Index Gmail threads into OpenSearch."""
    client = OpenSearchClient()

    # Check if OpenSearch is accessible
    if not client.health_check():
        click.echo(
            "✗ OpenSearch is not accessible. Run 'flo opensearch setup --start' first",
            err=True,
        )
        return

    # Create index
    click.echo("Creating index...")
    try:
        if client.create_index(recreate=recreate):
            click.echo("✓ Index ready")
        else:
            click.echo("✗ Failed to create index", err=True)
            return
    except Exception as e:
        click.echo(f"✗ Error creating index: {e}", err=True)
        return

    # Index threads
    click.echo(f"Indexing threads from {input}...")
    try:
        success, errors = client.index_threads(input)

        if success > 0:
            click.echo(f"\n✓ Successfully indexed {success} messages")

            # Show stats
            stats = client.get_stats()
            if stats:
                click.echo(f"✓ Total messages in index: {stats['total_messages']}")
                click.echo(f"✓ Unique threads: {stats['unique_threads']}")

        if errors > 0:
            click.echo(f"✗ Failed to index {errors} messages", err=True)

    except Exception as e:
        click.echo(f"✗ Error indexing threads: {e}", err=True)


@opensearch.command(name="search")
@click.argument("query")
@click.option("--size", "-n", default=10, help="Number of results to return")
def opensearch_search(query, size):
    """Search indexed Gmail threads."""
    client = OpenSearchClient()

    # Check if OpenSearch is accessible
    if not client.health_check():
        click.echo(
            "✗ OpenSearch is not accessible. Run 'flo opensearch setup --start' first",
            err=True,
        )
        return

    click.echo(f"Searching for: {query}")

    try:
        results = client.search(query, size=size)

        if not results:
            click.echo("No results found")
            return

        hits = results.get("hits", {}).get("hits", [])
        total = results.get("hits", {}).get("total", {}).get("value", 0)

        click.echo(f"\n✓ Found {total} matching messages (showing {len(hits)})\n")

        for i, hit in enumerate(hits, 1):
            source = hit.get("_source", {})
            highlight = hit.get("highlight", {})

            click.echo(f"{i}. Message ID: {source.get('message_id')}")
            click.echo(f"   Thread ID: {source.get('thread_id')}")
            click.echo(f"   Subject: {source.get('subject', 'No subject')}")
            click.echo(f"   From: {source.get('from', 'Unknown')}")
            click.echo(f"   Date: {source.get('date', 'Unknown')}")

            # Show highlighted matches
            if highlight.get("subject"):
                click.echo(f"   Match in subject: {highlight['subject'][0][:100]}...")
            elif highlight.get("body_text"):
                click.echo(f"   Match in body: {highlight['body_text'][0][:100]}...")

            click.echo()

    except Exception as e:
        click.echo(f"✗ Search error: {e}", err=True)


if __name__ == "__main__":
    cli()
