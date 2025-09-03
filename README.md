# Florian

This is a Python project that uses the GMail API to download threads of messages and index them for Retrieval Augmented Generation (RAG).

## System Requirements

- Python 3.12
- Poetry for dependency management
- Docker and Docker Compose (for OpenSearch)

## Quick Start

1. Clone the repository:

```bash
git clone https://github.com/yourusername/florian.git
cd florian
```

2. Create a virtual environment:

```bash
conda create -n florian python=3.12 -y
conda activate florian
```

```bash
python -m venv venv
source venv/bin/activate
```

3. Install dependencies:

```bash
poetry install
```

See [POETRY.md](assets/POETRY.md) for poetry installation instructions.

## Gmail API Setup

### 1. Enable Gmail API

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the Gmail API for your project
4. Configure the OAuth consent screen
5. Create OAuth 2.0 credentials (Desktop application type)
6. Download the credentials and save as `auth/credentials.json`

### 2. Authenticate

First-time authentication:

```bash
flo gmail auth
```

This will open a browser window for you to authorize the application. The access token will be saved to `auth/token.json` for future use.

## Usage

### CLI Commands

The Florian CLI provides several commands for interacting with Gmail:

```bash
# Show help
flo --help
flo gmail --help

# Authenticate with Gmail API
flo gmail auth

# List Gmail threads
flo gmail list --max-results 50
flo gmail list --query "is:unread"

# Search for threads you've participated in
flo gmail search  # Default: finds threads you've sent (from:me)
flo gmail search --query "from:me after:2024/1/1"  # Your replies after Jan 1, 2024
flo gmail search --max-results 1000 --output data/my_thread_ids.json

# Fetch full content for searched threads
flo gmail fetch-searched  # Uses data/thread_ids.json by default
flo gmail fetch-searched --limit 50  # Fetch only first 50 threads
flo gmail fetch-searched --input data/my_thread_ids.json --output data/my_threads.json

# Fetch Gmail threads with full content (direct method)
flo gmail fetch --max-threads 10
flo gmail fetch --query "from:important@example.com" --output data/threads.json

# Fetch a specific thread
flo gmail thread THREAD_ID --output data/thread.json
```

### Python API

```python
from florian.gmail.auth import GmailAuth
from florian.gmail.client import GmailClient

# Create authentication instance
auth = GmailAuth()

# Create Gmail client
client = GmailClient(auth)

# Fetch threads
threads = client.fetch_threads(
    query="is:unread",
    max_threads=10,
    save_to_file="data/gmail_threads.json"
)

# Process threads
for thread in threads:
    print(f"Thread ID: {thread['id']}")
    for message in thread['messages']:
        subject = message['headers'].get('subject', 'No subject')
        print(f"  - {subject}")
```

## Gmail Search Queries

You can use Gmail's search operators in the `--query` parameter:

- `is:unread` - Unread messages
- `from:sender@example.com` - Messages from specific sender
- `to:me` - Messages sent to you
- `subject:meeting` - Messages with "meeting" in subject
- `has:attachment` - Messages with attachments
- `after:2024/1/1` - Messages after a date
- `label:important` - Messages with specific label

Combine operators: `is:unread from:boss@company.com`

## OpenSearch Integration

Florian includes full OpenSearch integration for indexing and searching your email threads.

### Setup OpenSearch

```bash
# Start OpenSearch with Docker Compose
flo opensearch setup --start

# Check status
flo opensearch setup --status

# Stop OpenSearch
flo opensearch setup --stop
```

OpenSearch will be available at:

- OpenSearch API: http://localhost:9200
- OpenSearch Dashboards: http://localhost:5601

### Index Email Threads

```bash
# Index threads from default location (data/gmail_threads.json)
flo opensearch index

# Index from custom file
flo opensearch index --input data/my_threads.json

# Recreate index (delete existing data)
flo opensearch index --recreate
```

### Search Indexed Emails

```bash
# Search for emails
flo opensearch search "meeting"
flo opensearch search "project deadline" --size 20
```

### Complete Workflow

1. **Fetch emails from Gmail:**
   ```bash
   flo gmail search
   flo gmail fetch-searched
   ```

2. **Start OpenSearch:**
   ```bash
   flo opensearch setup --start
   ```

3. **Index emails:**
   ```bash
   flo opensearch index
   ```

4. **Search your emails:**
   ```bash
   flo opensearch search "important topic"
   ```

## Data Storage

Fetched threads are saved as JSON files with the following structure:

```json
{
  "id": "thread_id",
  "messages": [
    {
      "id": "message_id",
      "headers": {
        "from": "sender@example.com",
        "to": "recipient@example.com",
        "subject": "Email subject",
        "date": "Mon, 1 Jan 2024 12:00:00 -0000"
      },
      "body": {
        "text": "Plain text content",
        "html": "HTML content"
      }
    }
  ]
}
```
