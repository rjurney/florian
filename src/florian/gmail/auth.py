"""Gmail API authentication and service management."""

import os
import pickle

from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build

# Gmail API scopes - modify for required permissions
# gmail.readonly includes gmail.metadata and allows reading full message content
SCOPES = [
    "https://www.googleapis.com/auth/gmail.readonly",
]


class GmailAuth:
    """Handle Gmail API authentication and service creation."""

    def __init__(
        self,
        credentials_file: str = "auth/credentials.json",
        token_file: str = "auth/token.json",
    ):
        """Initialize Gmail authentication.

        Parameters
        ----------
        credentials_file : str
            Path to OAuth2 credentials file from Google Cloud Console
        token_file : str
            Path to store/load user access token
        """
        self.credentials_file = credentials_file
        self.token_file = token_file
        self.creds = None
        self.service = None

    def authenticate(self) -> Credentials | None:
        """Authenticate with Gmail API using OAuth2.

        Returns
        -------
        Credentials
            Authenticated credentials object
        """
        # Load existing token
        if os.path.exists(self.token_file):
            with open(self.token_file, "rb") as token:
                self.creds = pickle.load(token)

        # If there are no (valid) credentials, let the user log in
        if not self.creds or not self.creds.valid:
            if self.creds and self.creds.expired and self.creds.refresh_token:
                self.creds.refresh(Request())
            else:
                if not os.path.exists(self.credentials_file):
                    raise FileNotFoundError(
                        f"Credentials file not found: {self.credentials_file}\n"
                        "Please download OAuth2 credentials from Google Cloud Console "
                        "and save as credentials.json"
                    )

                flow = InstalledAppFlow.from_client_secrets_file(self.credentials_file, SCOPES)
                self.creds = flow.run_local_server(port=0)

            # Save the credentials for the next run
            with open(self.token_file, "wb") as token:
                pickle.dump(self.creds, token)

        return self.creds

    def get_service(self):
        """Get authenticated Gmail API service instance.

        Returns
        -------
        googleapiclient.discovery.Resource
            Gmail API service instance
        """
        if not self.service:
            if not self.creds:
                self.authenticate()
            self.service = build("gmail", "v1", credentials=self.creds)
        return self.service
