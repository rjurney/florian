#!/usr/bin/env python
"""Example script for using Florian Gmail API integration."""

from florian.gmail.auth import GmailAuth
from florian.gmail.client import GmailClient


def main():
    """Example of fetching Gmail threads."""
    # Create authentication instance
    auth = GmailAuth()

    # Create Gmail client
    client = GmailClient(auth)

    # Fetch recent threads (last 5)
    print("Fetching recent threads...")
    threads = client.fetch_threads(
        query="",  # Empty query fetches all threads
        max_threads=5,
        save_to_file="data/gmail_threads.json",
    )

    # Display summary
    for thread in threads:
        msg_count = len(thread.get("messages", []))
        first_msg = thread.get("messages", [{}])[0]
        subject = first_msg.get("headers", {}).get("subject", "No subject")

        print(f"\nThread ID: {thread['id']}")
        print(f"  Messages: {msg_count}")
        print(f"  Subject: {subject}")


if __name__ == "__main__":
    main()
