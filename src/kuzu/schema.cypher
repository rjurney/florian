// Represents an individual user with a UUID identifier
CREATE NODE TABLE Person(
    id UUID, 
    name STRING, 
    username STRING, 
    employeeId INT64, 
    department STRING, 
    PRIMARY KEY (id)
);

// Represents a unique email address with a UUID identifier
CREATE NODE TABLE EmailAddress(
    id UUID, 
    address STRING, 
    firstSeenDate DATE, 
    lastSeenDate DATE,
    PRIMARY KEY (id)
);

// Represents a single email message with a UUID identifier
CREATE NODE TABLE EmailMessage(
    id UUID, 
    messageId STRING, // retain for legacy system mapping
    subject STRING, 
    timestamp TIMESTAMP, 
    folder STRING,
    PRIMARY KEY (id)
);

// Represents a single file attachment with a UUID identifier
CREATE NODE TABLE Attachment(
    id UUID, 
    filename STRING, 
    fileSize INT64, 
    fileType STRING, 
    hash STRING,
    PRIMARY KEY (id)
);

// Represents the domain of an email address with a UUID identifier
CREATE NODE TABLE Domain(
    id UUID, 
    name STRING,
    PRIMARY KEY (id)
);

// Represents an email conversation thread with a UUID identifier
CREATE NODE TABLE Thread(
    id UUID,
    subject STRING,
    PRIMARY KEY (id)
);

// Links a person to one or more email addresses
CREATE REL TABLE HAS_EMAIL_ADDRESS(
    FROM Person TO EmailAddress, 
    isPrimary BOOLEAN
);

// Records that an email address sent a specific message
CREATE REL TABLE SENT(
    FROM EmailAddress TO EmailMessage, 
    isDraft BOOLEAN, 
    isForwarded BOOLEAN
);

// Defines the recipient list (To) for a message
CREATE REL TABLE TO(
    FROM EmailMessage TO EmailAddress
);

// Defines the carbon copy list (Cc) for a message
CREATE REL TABLE CC(
    FROM EmailMessage TO EmailAddress
);

// Defines the blind carbon copy list (Bcc) for a message
CREATE REL TABLE BCC(
    FROM EmailMessage TO EmailAddress
);

// Represents a message that is a reply to another message
CREATE REL TABLE REPLIED_TO(
    FROM EmailMessage TO EmailMessage,
    timestamp TIMESTAMP
);

// Connects an email message to a contained attachment
CREATE REL TABLE CONTAINS_ATTACHMENT(
    FROM EmailMessage TO Attachment
);

// Links an email address to its domain
CREATE REL TABLE PART_OF_DOMAIN(
    FROM EmailAddress TO Domain
);

// Links an email message to its conversation thread
CREATE REL TABLE PART_OF_THREAD(
    FROM EmailMessage TO Thread
);
