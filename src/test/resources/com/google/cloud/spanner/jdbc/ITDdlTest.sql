/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

NEW_CONNECTION;
-- Create table in autocommit mode

@EXPECT RESULT_SET 'AUTOCOMMIT',true
SHOW VARIABLE AUTOCOMMIT;
@EXPECT RESULT_SET 'READONLY',false
SHOW VARIABLE READONLY;

@EXPECT RESULT_SET
SELECT COUNT(*) AS ACTUAL, 0 AS EXPECTED
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME='VALID_DDL_AUTOCOMMIT';

CREATE TABLE VALID_DDL_AUTOCOMMIT (ID INT64 NOT NULL, BAR STRING(100)) PRIMARY KEY (ID);

@EXPECT RESULT_SET
SELECT COUNT(*) AS ACTUAL, 1 AS EXPECTED
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME='VALID_DDL_AUTOCOMMIT';


NEW_CONNECTION;
-- Try to create a table with an invalid SQL statement

@EXPECT RESULT_SET 'AUTOCOMMIT',true
SHOW VARIABLE AUTOCOMMIT;
@EXPECT RESULT_SET 'READONLY',false
SHOW VARIABLE READONLY;

@EXPECT RESULT_SET
SELECT COUNT(*) AS ACTUAL, 0 AS EXPECTED
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME='INVALID_DDL_AUTOCOMMIT';

@EXPECT EXCEPTION INVALID_ARGUMENT
CREATE TABLE INVALID_DDL_AUTOCOMMIT (ID INT64 NOT NULL, BAZ STRING(100), MISSING_DATA_TYPE_COL) PRIMARY KEY (ID);

@EXPECT RESULT_SET
SELECT COUNT(*) AS ACTUAL, 0 AS EXPECTED
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME='INVALID_DDL_AUTOCOMMIT';


NEW_CONNECTION;
-- Try to create a new table in a DDL_BATCH

-- Check that the table is not present
@EXPECT RESULT_SET
SELECT COUNT(*) AS ACTUAL, 0 AS EXPECTED
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME='VALID_SINGLE_DDL_IN_DDL_BATCH';

-- Change to DDL batch mode
SET AUTOCOMMIT = FALSE;
START BATCH DDL;

-- Execute the create table statement, but do not commit yet
CREATE TABLE VALID_SINGLE_DDL_IN_DDL_BATCH (ID INT64 NOT NULL, BAR STRING(100)) PRIMARY KEY (ID);

NEW_CONNECTION;
-- Transaction has not been committed, so the table should not be present
-- We do this in a new transaction, as selects are not allowed in a DDL_BATCH
@EXPECT RESULT_SET
SELECT COUNT(*) AS ACTUAL, 0 AS EXPECTED
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME='VALID_SINGLE_DDL_IN_DDL_BATCH';

-- Change to DDL batch mode again
SET AUTOCOMMIT = FALSE;
START BATCH DDL;

-- Execute the create table statement and do a commit
CREATE TABLE VALID_SINGLE_DDL_IN_DDL_BATCH (ID INT64 NOT NULL, BAR STRING(100)) PRIMARY KEY (ID);
RUN BATCH;

-- Go back to AUTOCOMMIT mode and check that the table was created
SET AUTOCOMMIT = TRUE;

@EXPECT RESULT_SET
SELECT COUNT(*) AS ACTUAL, 1 AS EXPECTED
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME='VALID_SINGLE_DDL_IN_DDL_BATCH';


NEW_CONNECTION;
-- Create two tables in one batch

-- First ensure that the tables do not exist
@EXPECT RESULT_SET
SELECT COUNT(*) AS ACTUAL, 0 AS EXPECTED
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME='VALID_MULTIPLE_DDL_IN_DDL_BATCH_1' OR TABLE_NAME='VALID_MULTIPLE_DDL_IN_DDL_BATCH_2';

-- Change to DDL batch mode
SET AUTOCOMMIT = FALSE;
START BATCH DDL;

-- Create two tables
CREATE TABLE VALID_MULTIPLE_DDL_IN_DDL_BATCH_1 (ID INT64 NOT NULL, BAR STRING(100)) PRIMARY KEY (ID);
CREATE TABLE VALID_MULTIPLE_DDL_IN_DDL_BATCH_2 (ID INT64 NOT NULL, BAR STRING(100)) PRIMARY KEY (ID);
-- Run the batch
RUN BATCH;

-- Switch to autocommit and verify that both tables exist
SET AUTOCOMMIT = TRUE;

@EXPECT RESULT_SET
SELECT COUNT(*) AS ACTUAL, 2 AS EXPECTED
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME='VALID_MULTIPLE_DDL_IN_DDL_BATCH_1' OR TABLE_NAME='VALID_MULTIPLE_DDL_IN_DDL_BATCH_2';


NEW_CONNECTION;
/*
 * Do a test that shows that a DDL batch might only execute some of the statements,
 * for example if data in a table prevents a unique index from being created.
 */
SET AUTOCOMMIT = FALSE;
START BATCH DDL;

CREATE TABLE TEST1 (ID INT64 NOT NULL, NAME STRING(100)) PRIMARY KEY (ID);
CREATE TABLE TEST2 (ID INT64 NOT NULL, NAME STRING(100)) PRIMARY KEY (ID);
RUN BATCH;

SET AUTOCOMMIT = TRUE;

@EXPECT RESULT_SET
SELECT COUNT(*) AS ACTUAL, 2 AS EXPECTED
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME='TEST1' OR TABLE_NAME='TEST2';

-- Fill the second table with some data that will prevent us from creating a unique index on
-- the name column.
INSERT INTO TEST2 (ID, NAME) VALUES (1, 'TEST');
INSERT INTO TEST2 (ID, NAME) VALUES (2, 'TEST');

-- Ensure the indices that we are to create do not exist
@EXPECT RESULT_SET
SELECT COUNT(*) AS ACTUAL, 0 AS EXPECTED
FROM INFORMATION_SCHEMA.INDEXES
WHERE (TABLE_NAME='TEST1' AND INDEX_NAME='IDX_TEST1')
   OR (TABLE_NAME='TEST2' AND INDEX_NAME='IDX_TEST2');

-- Try to create two unique indices in one batch
SET AUTOCOMMIT = FALSE;
START BATCH DDL;

CREATE UNIQUE INDEX IDX_TEST1 ON TEST1 (NAME);
CREATE UNIQUE INDEX IDX_TEST2 ON TEST2 (NAME);

@EXPECT EXCEPTION FAILED_PRECONDITION
RUN BATCH;

SET AUTOCOMMIT = TRUE;

-- Ensure that IDX_TEST1 was created and IDX_TEST2 was not.
@EXPECT RESULT_SET
SELECT COUNT(*) AS ACTUAL, 1 AS EXPECTED
FROM INFORMATION_SCHEMA.INDEXES
WHERE TABLE_NAME='TEST1' AND INDEX_NAME='IDX_TEST1';

@EXPECT RESULT_SET
SELECT COUNT(*) AS ACTUAL, 0 AS EXPECTED
FROM INFORMATION_SCHEMA.INDEXES
WHERE TABLE_NAME='TEST2' AND INDEX_NAME='IDX_TEST2';

NEW_CONNECTION;
/* Verify that empty DDL batches are accepted. */
START BATCH DDL;
RUN BATCH;

START BATCH DDL;
ABORT BATCH;

NEW_CONNECTION;
-- Set proto descriptors using relative path to the descriptors.pb file. This gets applied for next DDL statement
SET PROTO_DESCRIPTORS_FILE_PATH = 'src/test/resources/com/google/cloud/spanner/jdbc/it/descriptors.pb';
-- Check if Proto descriptors is set
@EXPECT RESULT_SET 'PROTO_DESCRIPTORS'
SHOW VARIABLE PROTO_DESCRIPTORS;

CREATE PROTO BUNDLE (examples.spanner.music.Genre);
-- Check if Proto descriptors is reset to null
@EXPECT RESULT_SET 'PROTO_DESCRIPTORS',null
SHOW VARIABLE PROTO_DESCRIPTORS;

-- Set Proto Descriptor as base64 string. This gets applied to all statements in next DDL batch
SET PROTO_DESCRIPTORS = 'CvYCCgxzaW5nZXIucHJvdG8SFmV4YW1wbGVzLnNwYW5uZXIubXVzaWMi6gEKClNpbmdlckluZm8SIAoJc2luZ2VyX2lkGAEgASgDSABSCHNpbmdlcklkiAEBEiIKCmJpcnRoX2RhdGUYAiABKAlIAVIJYmlydGhEYXRliAEBEiUKC25hdGlvbmFsaXR5GAMgASgJSAJSC25hdGlvbmFsaXR5iAEBEjgKBWdlbnJlGAQgASgOMh0uZXhhbXBsZXMuc3Bhbm5lci5tdXNpYy5HZW5yZUgDUgVnZW5yZYgBAUIMCgpfc2luZ2VyX2lkQg0KC19iaXJ0aF9kYXRlQg4KDF9uYXRpb25hbGl0eUIICgZfZ2VucmUqLgoFR2VucmUSBwoDUE9QEAASCAoESkFaWhABEggKBEZPTEsQAhIICgRST0NLEANCKQoYY29tLmdvb2dsZS5jbG91ZC5zcGFubmVyQgtTaW5nZXJQcm90b1AAYgZwcm90bzM=';

@EXPECT RESULT_SET 'PROTO_DESCRIPTORS'
SHOW VARIABLE PROTO_DESCRIPTORS;

START BATCH DDL;
ALTER PROTO BUNDLE INSERT (examples.spanner.music.SingerInfo);
CREATE TABLE Singers (
     SingerId   INT64 NOT NULL,
     FirstName  STRING(1024),
     LastName   STRING(1024),
     SingerInfo examples.spanner.music.SingerInfo,
     SingerGenre examples.spanner.music.Genre
) PRIMARY KEY (SingerId);
-- Run the batch
RUN BATCH;

-- Check if Proto descriptors is reset to null
@EXPECT RESULT_SET 'PROTO_DESCRIPTORS',null
SHOW VARIABLE PROTO_DESCRIPTORS;
-- Check that the table is created
@EXPECT RESULT_SET
SELECT COUNT(*) AS ACTUAL, 1 AS EXPECTED
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME='Singers';
