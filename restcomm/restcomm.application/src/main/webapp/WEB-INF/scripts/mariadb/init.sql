CREATE DATABASE IF NOT EXISTS restcomm;
USE restcomm;

CREATE TABLE restcomm_organizations (
sid VARCHAR(34) NOT NULL PRIMARY KEY,
domain_name VARCHAR(255) NOT NULL UNIQUE,
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL,
status VARCHAR(16) NOT NULL
);

CREATE TABLE restcomm_instance_id (
instance_id VARCHAR(34) NOT NULL PRIMARY KEY,
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL,
host VARCHAR(255) NOT NULL
);

CREATE TABLE restcomm_accounts (
sid VARCHAR(34) NOT NULL PRIMARY KEY,
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL,
email_address MEDIUMTEXT NOT NULL,
friendly_name VARCHAR(64) NOT NULL,
parent_sid VARCHAR(34),
type VARCHAR(8) NOT NULL,
status VARCHAR(16) NOT NULL,
auth_token VARCHAR(32) NOT NULL,
role VARCHAR(64) NOT NULL,
uri MEDIUMTEXT NOT NULL,
organization_sid VARCHAR(34) NOT NULL
);

CREATE TABLE restcomm_announcements (
sid VARCHAR(34) NOT NULL PRIMARY KEY,
date_created DATETIME NOT NULL,
account_sid VARCHAR(34),
gender VARCHAR(8) NOT NULL,
language VARCHAR(16) NOT NULL,
text VARCHAR(32) NOT NULL,
uri MEDIUMTEXT NOT NULL
);

CREATE TABLE restcomm_available_phone_numbers (
friendly_name VARCHAR(64) NOT NULL,
phone_number VARCHAR(15) NOT NULL PRIMARY KEY,
lata SMALLINT,
rate_center VARCHAR(32),
latitude DOUBLE,
longitude DOUBLE,
region VARCHAR(2),
postal_code INT,
iso_country VARCHAR(2) NOT NULL,
voice_capable BOOLEAN,
sms_capable BOOLEAN,
mms_capable BOOLEAN,
fax_capable BOOLEAN,
cost VARCHAR(10)
);

CREATE TABLE restcomm_outgoing_caller_ids (
sid VARCHAR(34) NOT NULL PRIMARY KEY,
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL,
friendly_name VARCHAR(64) NOT NULL,
account_sid VARCHAR(34) NOT NULL,
phone_number VARCHAR(15) NOT NULL,
uri MEDIUMTEXT NOT NULL
);

CREATE TABLE restcomm_http_cookies (
sid VARCHAR(34) NOT NULL PRIMARY KEY,
comment MEDIUMTEXT,
domain MEDIUMTEXT,
expiration_date DATETIME,
name MEDIUMTEXT NOT NULL,
path MEDIUMTEXT,
value MEDIUMTEXT,
version INT
);

CREATE TABLE restcomm_incoming_phone_numbers (
sid VARCHAR(34) NOT NULL PRIMARY KEY,
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL,
friendly_name VARCHAR(256) NOT NULL,
account_sid VARCHAR(34) NOT NULL,
phone_number VARCHAR(30) NOT NULL,
api_version VARCHAR(10) NOT NULL,
voice_caller_id_lookup BOOLEAN NOT NULL,
voice_url MEDIUMTEXT,
voice_method VARCHAR(4),
voice_fallback_url MEDIUMTEXT,
voice_fallback_method VARCHAR(4),
status_callback MEDIUMTEXT,
status_callback_method VARCHAR(4),
voice_application_sid VARCHAR(34),
sms_url MEDIUMTEXT,
sms_method VARCHAR(4),
sms_fallback_url MEDIUMTEXT,
sms_fallback_method VARCHAR(4),
sms_application_sid VARCHAR(34),
uri MEDIUMTEXT NOT NULL,
voice_capable BOOLEAN,
sms_capable BOOLEAN,
mms_capable BOOLEAN,
fax_capable BOOLEAN,
pure_sip BOOLEAN,
cost VARCHAR(10),
ussd_url MEDIUMTEXT,
ussd_method VARCHAR(4),
ussd_fallback_url MEDIUMTEXT,
ussd_fallback_method VARCHAR(4),
ussd_application_sid VARCHAR(34),
refer_url MEDIUMTEXT,
refer_method VARCHAR(4),
refer_application_sid VARCHAR(34),
organization_sid VARCHAR(34) NOT NULL
);

CREATE TABLE restcomm_applications (
sid VARCHAR(34) NOT NULL PRIMARY KEY,
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL,
friendly_name VARCHAR(64) NOT NULL,
account_sid VARCHAR(34) NOT NULL,
api_version VARCHAR(10) NOT NULL,
voice_caller_id_lookup BOOLEAN NOT NULL,
uri MEDIUMTEXT NOT NULL,
rcml_url MEDIUMTEXT,
kind VARCHAR(5)
);

CREATE TABLE restcomm_call_detail_records (
sid VARCHAR(1000) NOT NULL PRIMARY KEY,
parent_call_sid VARCHAR(1000),
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL,
account_sid VARCHAR(34) NOT NULL,
sender VARCHAR(255) NOT NULL,
recipient VARCHAR(64) NOT NULL,
phone_number_sid VARCHAR(34),
status VARCHAR(20) NOT NULL,
start_time DATETIME,
end_time DATETIME,
duration INT,
price VARCHAR(8),
direction VARCHAR(20) NOT NULL,
answered_by VARCHAR(64),
api_version VARCHAR(10) NOT NULL,
forwarded_from VARCHAR(30),
caller_name VARCHAR(50),
uri MEDIUMTEXT NOT NULL,
call_path VARCHAR(255),
ring_duration INT,
instanceid VARCHAR(255),
conference_sid VARCHAR(34),
muted BOOLEAN,
start_conference_on_enter BOOLEAN,
end_conference_on_exit BOOLEAN,
on_hold BOOLEAN,
ms_id VARCHAR(34)
);

CREATE TABLE restcomm_conference_detail_records (
sid VARCHAR(34) NOT NULL PRIMARY KEY,
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL,
account_sid VARCHAR(34) NOT NULL,
status VARCHAR(100) NOT NULL,
friendly_name VARCHAR(1000),
api_version VARCHAR(10) NOT NULL,
uri MEDIUMTEXT NOT NULL,
master_ms_id VARCHAR(34),
master_conference_endpoint_id VARCHAR(1000),
master_present BOOLEAN NOT NULL DEFAULT TRUE,
master_ivr_endpoint_id VARCHAR(1000),
master_ivr_endpoint_session_id VARCHAR(1000),
master_bridge_endpoint_id VARCHAR(1000),
master_bridge_endpoint_session_id VARCHAR(1000),
master_bridge_conn_id VARCHAR(1000),
master_ivr_conn_id VARCHAR(1000),
moderator_present BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE restcomm_clients (
sid VARCHAR(34) NOT NULL PRIMARY KEY,
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL,
account_sid VARCHAR(34) NOT NULL,
api_version VARCHAR(10) NOT NULL,
friendly_name VARCHAR(64) NOT NULL,
login VARCHAR(64) NOT NULL,
password VARCHAR(64) NOT NULL,
status INT NOT NULL,
voice_url MEDIUMTEXT,
voice_method VARCHAR(4),
voice_fallback_url MEDIUMTEXT,
voice_fallback_method VARCHAR(4),
voice_application_sid VARCHAR(34),
uri MEDIUMTEXT NOT NULL,
push_client_identity VARCHAR(34),
password_algorithm VARCHAR(34)
);

CREATE TABLE restcomm_registrations (
sid VARCHAR(34) NOT NULL PRIMARY KEY,
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL,
date_expires DATETIME NOT NULL,
address_of_record MEDIUMTEXT NOT NULL,
display_name VARCHAR(255),
user_name VARCHAR(64) NOT NULL,
user_agent MEDIUMTEXT,
ttl INT NOT NULL,
location MEDIUMTEXT NOT NULL,
webrtc BOOLEAN NOT NULL DEFAULT FALSE,
instanceid VARCHAR(255),
isLBPresent BOOLEAN NOT NULL DEFAULT FALSE,
organization_sid VARCHAR(34) NOT NULL
);

CREATE TABLE restcomm_short_codes (
sid VARCHAR(34) NOT NULL PRIMARY KEY,
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL,
friendly_name VARCHAR(64) NOT NULL,
account_sid VARCHAR(34) NOT NULL,
short_code INT NOT NULL,
api_version VARCHAR(10) NOT NULL,
sms_url MEDIUMTEXT,
sms_method VARCHAR(4),
sms_fallback_url MEDIUMTEXT,
sms_fallback_method VARCHAR(4),
uri MEDIUMTEXT NOT NULL
);

CREATE TABLE restcomm_sms_messages (
sid VARCHAR(34) NOT NULL PRIMARY KEY,
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL,
date_sent DATETIME,
account_sid VARCHAR(34) NOT NULL,
sender VARCHAR(255) NOT NULL,
recipient VARCHAR(64) NOT NULL,
body VARCHAR(999) NOT NULL,
status VARCHAR(20) NOT NULL,
direction VARCHAR(14) NOT NULL,
price VARCHAR(8) NOT NULL,
api_version VARCHAR(10) NOT NULL,
uri MEDIUMTEXT NOT NULL,
smpp_message_id MEDIUMTEXT,
error_code BIGINT
);

CREATE TABLE restcomm_recordings (
sid VARCHAR(34) NOT NULL PRIMARY KEY,
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL,
account_sid VARCHAR(34) NOT NULL,
call_sid VARCHAR(1000) NOT NULL,
duration DOUBLE NOT NULL,
api_version VARCHAR(10) NOT NULL,
uri MEDIUMTEXT NOT NULL,
file_uri MEDIUMTEXT,
s3_uri MEDIUMTEXT
);

CREATE TABLE restcomm_transcriptions (
sid VARCHAR(34) NOT NULL PRIMARY KEY,
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL,
account_sid VARCHAR(34) NOT NULL,
status VARCHAR(11) NOT NULL,
recording_sid VARCHAR(34) NOT NULL,
duration DOUBLE NOT NULL,
transcription_text MEDIUMTEXT NOT NULL,
price VARCHAR(8) NOT NULL,
uri MEDIUMTEXT NOT NULL
);

CREATE TABLE restcomm_notifications (
sid VARCHAR(34) NOT NULL PRIMARY KEY,
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL,
account_sid VARCHAR(34) NOT NULL,
call_sid VARCHAR(1000),
api_version VARCHAR(10) NOT NULL,
log TINYINT NOT NULL,
error_code SMALLINT NOT NULL,
more_info MEDIUMTEXT NOT NULL,
message_text MEDIUMTEXT NOT NULL,
message_date DATETIME NOT NULL,
request_url MEDIUMTEXT NOT NULL,
request_method VARCHAR(4) NOT NULL,
request_variables MEDIUMTEXT NOT NULL,
response_headers MEDIUMTEXT,
response_body MEDIUMTEXT,
uri MEDIUMTEXT NOT NULL
);

CREATE TABLE restcomm_sand_boxes (
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL,
pin VARCHAR(8) NOT NULL,
account_sid VARCHAR(34) NOT NULL PRIMARY KEY,
phone_number VARCHAR(15) NOT NULL,
application_sid VARCHAR(34) NOT NULL,
api_version VARCHAR(10) NOT NULL,
voice_url MEDIUMTEXT,
voice_method VARCHAR(4),
sms_url MEDIUMTEXT,
sms_method VARCHAR(4),
status_callback MEDIUMTEXT,
status_callback_method VARCHAR(4),
uri MEDIUMTEXT NOT NULL
);

CREATE TABLE restcomm_gateways (
sid VARCHAR(34) NOT NULL PRIMARY KEY,
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL,
friendly_name VARCHAR(64),
user_name VARCHAR(255),
password VARCHAR(255),
proxy MEDIUMTEXT NOT NULL,
register BOOLEAN NOT NULL,
ttl INT NOT NULL,
uri MEDIUMTEXT NOT NULL
);

CREATE TABLE restcomm_geolocation(
sid VARCHAR(34) NOT NULL PRIMARY KEY,
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL,
date_executed DATETIME NOT NULL,
account_sid VARCHAR(34) NOT NULL,
source VARCHAR(30),
device_identifier VARCHAR(30) NOT NULL,
geolocation_type VARCHAR(15) NOT NULL,
response_status VARCHAR(30),
cell_id VARCHAR(10),
location_area_code VARCHAR(10),
mobile_country_code INTEGER,
mobile_network_code VARCHAR(3),
network_entity_address BIGINT,
age_of_location_info INTEGER,
device_latitude VARCHAR(15),
device_longitude VARCHAR(15),
accuracy BIGINT,
physical_address VARCHAR(50),
internet_address VARCHAR(50),
formatted_address VARCHAR(200),
location_timestamp DATETIME,
event_geofence_latitude VARCHAR(15),
event_geofence_longitude VARCHAR(15),
radius BIGINT,
geolocation_positioning_type VARCHAR(15),
last_geolocation_response VARCHAR(15),
cause VARCHAR(150),
api_version VARCHAR(10) NOT NULL,
uri MEDIUMTEXT NOT NULL);

CREATE TABLE update_scripts (
script VARCHAR(255) NOT NULL,
date_executed DATETIME NOT NULL
);

CREATE TABLE restcomm_media_servers (
ms_id INT PRIMARY KEY AUTO_INCREMENT,
local_ip VARCHAR(34) NOT NULL,
local_port INT NOT NULL,
remote_ip VARCHAR(34) NOT NULL UNIQUE,
remote_port INT NOT NULL,
compatibility VARCHAR(34) DEFAULT "rms",
response_timeout VARCHAR(34),
external_address VARCHAR(34)
);

CREATE TABLE restcomm_media_resource_broker_entity (
conference_sid VARCHAR(34) NOT NULL,
slave_ms_id VARCHAR(34) NOT NULL,
slave_ms_bridge_ep_id VARCHAR(34),
slave_ms_cnf_ep_id VARCHAR(34),
is_bridged_together BOOLEAN NOT NULL DEFAULT FALSE,
PRIMARY KEY (conference_sid , slave_ms_id)
);

CREATE TABLE restcomm_extensions_configuration (
sid VARCHAR(34) NOT NULL PRIMARY KEY,
extension VARCHAR(255) NOT NULL,
configuration_data LONGTEXT NOT NULL,
configuration_type VARCHAR(255) NOT NULL,
date_created DATETIME NOT NULL,
date_updated DATETIME,
enabled  BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE restcomm_profile_associations(
target_sid VARCHAR(34) NOT NULL PRIMARY KEY,
profile_sid VARCHAR(34) NOT NULL,
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL
);

CREATE TABLE restcomm_accounts_extensions (
account_sid VARCHAR(34) NOT NULL,
extension_sid VARCHAR(34) NOT NULL,
configuration_data LONGTEXT NOT NULL,
PRIMARY KEY (account_sid, extension_sid)
);

CREATE TABLE restcomm_profiles (
sid VARCHAR(34) NOT NULL PRIMARY KEY,
document LONGTEXT NOT NULL,
date_created DATETIME NOT NULL,
date_updated DATETIME NOT NULL
);

INSERT INTO restcomm_organizations VALUES(
"ORafbe225ad37541eba518a74248f0ac4c",
"default.restcomm.com",
Date("2017-04-19"),
Date("2017-04-19"),
"active"
);

INSERT INTO restcomm_accounts VALUES (
"ACae6e420f425248d6a26948c17a9e2acf",
Date("2012-04-24"),
Date("2012-04-24"),
"administrator@company.com",
"Default Administrator Account",
null,
"Full",
"uninitialized",
"77f8c12cc7b8f8423e5c38b035249166",
"Administrator",
"/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf",
"ORafbe225ad37541eba518a74248f0ac4c");

/* Create demo Applications */
INSERT INTO restcomm_applications VALUES('AP73926e7113fa4d95981aa96b76eca854','2015-09-23 06:56:04.108000','2015-09-23 06:56:04.108000','rvdCollectVerbDemo','ACae6e420f425248d6a26948c17a9e2acf','2012-04-24',FALSE,'/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/Applications/AP73926e7113fa4d95981aa96b76eca854','/restcomm-rvd/services/apps/AP73926e7113fa4d95981aa96b76eca854/controller','voice');
INSERT INTO restcomm_applications VALUES('AP81cf45088cba4abcac1261385916d582','2015-09-23 06:56:17.977000','2015-09-23 06:56:17.977000','rvdESDemo','ACae6e420f425248d6a26948c17a9e2acf','2012-04-24',FALSE,'/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/Applications/AP81cf45088cba4abcac1261385916d582','/restcomm-rvd/services/apps/AP81cf45088cba4abcac1261385916d582/controller','voice');
INSERT INTO restcomm_applications VALUES('APb70c33bf0b6748f09eaec97030af36f3','2015-09-23 06:56:26.120000','2015-09-23 06:56:26.120000','rvdSayVerbDemo','ACae6e420f425248d6a26948c17a9e2acf','2012-04-24',FALSE,'/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/Applications/APb70c33bf0b6748f09eaec97030af36f3','/restcomm-rvd/services/apps/APb70c33bf0b6748f09eaec97030af36f3/controller','voice');

/* Bind default DID to demo apps */
INSERT INTO restcomm_incoming_phone_numbers VALUES('PNdd7a0a0248244615978bd5781598e5eb','2013-10-04 17:42:02.500000000','2013-10-04 17:42:02.500000000','234','ACae6e420f425248d6a26948c17a9e2acf','+1234','2012-04-24',FALSE,'/restcomm/demos/hello-play.xml','POST',NULL,'POST',NULL,'POST',NULL,NULL,'POST',NULL,'POST',NULL,'/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/IncomingPhoneNumbers/PNdd7a0a0248244615978bd5781598e5eb', true, false, false, false, true, 0.0, null, null, null, null, null, null, null, null, 'ORafbe225ad37541eba518a74248f0ac4c');
INSERT INTO restcomm_incoming_phone_numbers VALUES('PN146638eec1e2415d832785e30d227598','2013-10-11 14:56:08.549000000','2013-10-11 14:56:08.549000000','This app plays the Hello World msg and requires Text-to-speech ','ACae6e420f425248d6a26948c17a9e2acf','+1235','2012-04-24',FALSE,'/restcomm/demos/hello-world.xml','POST',NULL,'POST',NULL,'POST',NULL,NULL,'POST',NULL,'POST',NULL,'/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/IncomingPhoneNumbers/PN146638eec1e2415d832785e30d227598', true, false, false, false, true, 0.0, null, null, null, null, null, null, null, null, 'ORafbe225ad37541eba518a74248f0ac4c');
INSERT INTO restcomm_incoming_phone_numbers VALUES('PNabf9c98b95d64b26b5993ad52e809566','2013-10-11 14:55:56.670000000','2013-10-11 14:55:56.670000000','This app uses the collect verb to get user input','ACae6e420f425248d6a26948c17a9e2acf','+1236','2012-04-24',FALSE,'/restcomm/demos/gather/hello-gather.xml','POST',NULL,'POST',NULL,'POST',NULL,NULL,'POST',NULL,'POST',NULL,'/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/IncomingPhoneNumbers/PNabf9c98b95d64b26b5993ad52e809566', true, false, false, false, true, 0.0, null, null, null, null, null, null, null, null, 'ORafbe225ad37541eba518a74248f0ac4c');
INSERT INTO restcomm_incoming_phone_numbers VALUES('PN91275300c95547039c3723a1e58b5662','2013-10-31 22:12:19.318000000','2013-10-31 22:12:19.318000000','This app requires that you configure the sip:username@ipaddress:port','ACae6e420f425248d6a26948c17a9e2acf','+1237','2012-04-24',FALSE,'/restcomm/demos/dial/sip/dial-sip.xml','POST',NULL,'POST',NULL,'POST',NULL,NULL,'POST',NULL,'POST',NULL,'/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/IncomingPhoneNumbers/PN91275300c95547039c3723a1e58b5662', true, false, false, false, true, 0.0, null, null, null, null, null, null, null, null, 'ORafbe225ad37541eba518a74248f0ac4c');
INSERT INTO restcomm_incoming_phone_numbers VALUES('PN0b4201c6c87749f29367e6cf000686cb','2013-11-04 12:14:10.520000000','2013-11-04 12:14:10.520000000','This app calls registered restcomm client Alice ','ACae6e420f425248d6a26948c17a9e2acf','+1238','2012-04-24',FALSE,'/restcomm/demos/dial/client/dial-client.xml','POST',NULL,'POST',NULL,'POST',NULL,NULL,'POST',NULL,'POST',NULL,'/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/IncomingPhoneNumbers/PN0b4201c6c87749f29367e6cf000686cb', true, false, false, false, true, 0.0, null, null, null, null, null, null, null, null, 'ORafbe225ad37541eba518a74248f0ac4c');
INSERT INTO restcomm_incoming_phone_numbers VALUES('PN9f27f81e725640d988486ff15f48ad18','2013-11-04 12:42:11.530000000','2013-11-04 12:42:11.530000000','This app join a conf bridge with wait music playing ','ACae6e420f425248d6a26948c17a9e2acf','+1310','2012-04-24',FALSE,'/restcomm/demos/dial/conference/dial-conference.xml','POST',NULL,'POST',NULL,'POST',NULL,NULL,'POST',NULL,'POST',NULL,'/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/IncomingPhoneNumbers/PN9f27f81e725640d988486ff15f48ad18', true, false, false, false, true, 0.0, null, null, null, null, null, null, null, null, 'ORafbe225ad37541eba518a74248f0ac4c');
INSERT INTO restcomm_incoming_phone_numbers VALUES('PN3862668c51634d18ae027c63438b4583','2013-11-04 12:42:44.777000000','2013-11-04 12:42:44.777000000','This app adds you to a conf bridge as a moderator','ACae6e420f425248d6a26948c17a9e2acf','+1311','2012-04-24',FALSE,'/restcomm/demos/dial/conference/dial-conference-moderator.xml','POST',NULL,'POST',NULL,'POST',NULL,NULL,'POST',NULL,'POST',NULL,'/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/IncomingPhoneNumbers/PN3862668c51634d18ae027c63438b4583', true, false, false, false, true, 0.0, null, null, null, null, null, null, null, null, 'ORafbe225ad37541eba518a74248f0ac4c');
INSERT INTO restcomm_incoming_phone_numbers VALUES('PNc2b81d68a221482ea387b6b4e2cbd9d7','2014-02-17 22:36:58.008000000','2014-02-17 22:36:58.008000000','This makes a call to a basic RVD app ','ACae6e420f425248d6a26948c17a9e2acf','+1239','2012-04-24',FALSE,NULL,'POST',NULL,'POST',NULL,'POST','APb70c33bf0b6748f09eaec97030af36f3',NULL,'POST',NULL,'POST',NULL,'/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/IncomingPhoneNumbers/PNc2b81d68a221482ea387b6b4e2cbd9d7',true, false, false, false, true, 0.0, null, null, null, null, null, null, null, null, 'ORafbe225ad37541eba518a74248f0ac4c');
INSERT INTO restcomm_incoming_phone_numbers VALUES('PN46678e5b01d44973bf184f6527bc33f7','2014-02-17 22:37:08.709000000','2014-02-17 22:37:08.709000000','This is an IVR app that maps user input to specific action','ACae6e420f425248d6a26948c17a9e2acf','+1240','2012-04-24',FALSE,NULL,'POST',NULL,'POST',NULL,'POST','AP73926e7113fa4d95981aa96b76eca854',NULL,'POST',NULL,'POST',NULL,'/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/IncomingPhoneNumbers/PN46678e5b01d44973bf184f6527bc33f7',true, false, false, false, true, 0.0, null, null, null, null, null, null, null, null, 'ORafbe225ad37541eba518a74248f0ac4c');
INSERT INTO restcomm_incoming_phone_numbers VALUES('PNb43ed9e641364277b6432547ff1109e9','2014-02-17 22:37:19.392000000','2014-02-17 22:37:19.392000000','RVD external services app, customer ID 1 or 2 ','ACae6e420f425248d6a26948c17a9e2acf','+1241','2012-04-24',FALSE,NULL,'POST',NULL,'POST',NULL,'POST','AP81cf45088cba4abcac1261385916d582',NULL,'POST',NULL,'POST',NULL,'/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/IncomingPhoneNumbers/PNb43ed9e641364277b6432547ff1109e9',true, false, false, false, true, 0.0, null, null, null, null, null, null, null, null, 'ORafbe225ad37541eba518a74248f0ac4c');
INSERT INTO restcomm_incoming_phone_numbers VALUES('PN78341988ed59478d89a37bd820d94fb8','2017-02-08 11:11:23.948000000','2017-02-08 11:11:23.948000000','This app plays the video demonstration and requires XMS media server','ACae6e420f425248d6a26948c17a9e2acf','+1242','2012-04-24',FALSE,'/restcomm/demos/video-play.xml','POST',NULL,'POST',NULL,'POST',NULL,NULL,'POST',NULL,'POST',NULL,'/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/IncomingPhoneNumbers/PN78341988ed59478d89a37bd820d94fb8',true, false, false, false, true, 0.0, null, null, null, null, null, null, null, null, 'ORafbe225ad37541eba518a74248f0ac4c');
INSERT INTO restcomm_incoming_phone_numbers VALUES('PN9154cd100e894cccb9846542b831f8f0','2017-03-08 04:29:30.827000000','2017-03-08 04:29:30.827000000','This app records a video message and requires XMS media server','ACae6e420f425248d6a26948c17a9e2acf','+1243','2012-04-24',FALSE,'/restcomm/demos/video-record.xml','POST',NULL,'POST',NULL,'POST',NULL,NULL,'POST',NULL,'POST',NULL,'/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/IncomingPhoneNumbers/PN9154cd100e894cccb9846542b831f8f0', true, false, false, false, true, 0.0, null, null, null, null, null, null, null, null, 'ORafbe225ad37541eba518a74248f0ac4c');
INSERT INTO restcomm_incoming_phone_numbers VALUES('PN268b3f55d3a84a70aae8b78bde3443b5','2017-04-13 22:03:27.925000000','2017-04-13 22:03:27.925000000','This app joins a video conf bridge with wait music playing and requires XMS media server','ACae6e420f425248d6a26948c17a9e2acf','+1244','2012-04-24',FALSE,'/restcomm/demos/video-conference.xml','POST',NULL,'POST',NULL,'POST',NULL,NULL,'POST',NULL,'POST',NULL,'/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/IncomingPhoneNumbers/PN268b3f55d3a84a70aae8b78bde3443b5', true, false, false, false, true, 0.0, null, null, null, null, null, null, null, null, 'ORafbe225ad37541eba518a74248f0ac4c');
INSERT INTO restcomm_incoming_phone_numbers VALUES('PNa956a87b060b4b93bf432fce19fe79bf','2017-04-13 22:04:04.258000000','2017-04-13 22:04:04.258000000','This app adds you to a video conf bridge as a moderator and requires XMS media server','ACae6e420f425248d6a26948c17a9e2acf','+1245','2012-04-24',FALSE,'/restcomm/demos/video-conference-moderator.xml','POST',NULL,'POST',NULL,'POST',NULL,NULL,'POST',NULL,'POST',NULL,'/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/IncomingPhoneNumbers/PNa956a87b060b4b93bf432fce19fe79bf', true, false, false, false, true, 0.0, null, null, null, null, null, null, null, null, 'ORafbe225ad37541eba518a74248f0ac4c');
INSERT INTO restcomm_incoming_phone_numbers VALUES('PN5eadc8c3b26a495a842bbff6aecc9f6c','2017-09-29 19:34:10.679000000','2017-09-29 19:34:10.679000000','This app calls registered restcomm client Alice using XMS for video','ACae6e420f425248d6a26948c17a9e2acf','+1246','2012-04-24',FALSE,'/restcomm/demos/video-dial-client.xml','POST',NULL,'POST',NULL,'POST',NULL,NULL,'POST',NULL,'POST',NULL,'/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/IncomingPhoneNumbers/PN5eadc8c3b26a495a842bbff6aecc9f6c', true, false, false, false, true, 0.0, null, null, null, null, null, null, null, null, 'ORafbe225ad37541eba518a74248f0ac4c');

/* Create index on restcomm_call_detail_records on conference_sid column */
CREATE INDEX idx_cdr_conference_sid ON restcomm_call_detail_records (conference_sid);

/* Create index on restcomm_call_detail_records on conference_sid column */
CREATE INDEX idx_cdr_conference_status ON restcomm_conference_detail_records (status);

/* Create index on restcomm_sms_messages on smpp_message_id column */
CREATE INDEX idx_restcomm_sms_messages_smpp_message_id ON restcomm_sms_messages (smpp_message_id);

DELIMITER //
DROP PROCEDURE IF EXISTS addConferenceDetailRecord;
CREATE PROCEDURE addConferenceDetailRecord(	IN in_sid VARCHAR(34),
									IN in_date_created DATETIME,
									IN in_date_updated DATETIME,
									IN in_account_sid VARCHAR(34),
									IN in_status VARCHAR(100),
									IN in_friendly_name VARCHAR(60),
									IN in_api_version VARCHAR(10),
									IN in_uri MEDIUMTEXT,
									IN in_master_ms_id VARCHAR(34),
									IN master_present BOOLEAN )
BEGIN
	IF EXISTS(SELECT * FROM restcomm_conference_detail_records WHERE friendly_name=in_friendly_name AND account_sid=in_account_sid AND status LIKE 'RUNNING%')
	THEN
		SELECT * FROM restcomm_conference_detail_records WHERE friendly_name=in_friendly_name AND account_sid=in_account_sid AND status LIKE 'RUNNING%';
	ELSE
		INSERT INTO restcomm_conference_detail_records (sid, date_created, date_updated, account_sid, status, friendly_name, api_version, uri, master_ms_id, master_present) VALUES (in_sid, in_date_created, in_date_updated, in_account_sid, in_status, in_friendly_name, in_api_version, in_uri, in_master_ms_id, master_present);

	END IF;

END //
DELIMITER ;

DELIMITER //
DROP PROCEDURE IF EXISTS completeConferenceDetailRecord;
CREATE PROCEDURE completeConferenceDetailRecord
	(IN in_sid VARCHAR(100)
	,IN in_status VARCHAR(100)
	,IN in_slave_ms_id VARCHAR(100)
	,IN in_date_updated TIMESTAMP
	,IN amIMaster BOOLEAN
	,OUT completed BOOLEAN)

BEGIN

START TRANSACTION;
	SET completed=FALSE;
	IF(amIMaster) THEN
		UPDATE restcomm_conference_detail_records SET restcomm_conference_detail_records.master_present=FALSE,restcomm_conference_detail_records.date_updated=in_date_updated WHERE restcomm_conference_detail_records.sid=in_sid;
		IF NOT EXISTS (SELECT restcomm_media_resource_broker_entity.conference_sid,restcomm_media_resource_broker_entity.slave_ms_id,restcomm_media_resource_broker_entity.slave_ms_bridge_ep_id,restcomm_media_resource_broker_entity.slave_ms_cnf_ep_id,restcomm_media_resource_broker_entity.is_bridged_together FROM restcomm_media_resource_broker_entity WHERE conference_sid=in_sid ) THEN
			UPDATE restcomm_conference_detail_records SET status=in_status,date_updated=in_date_updated WHERE sid=in_sid;
			SET completed=TRUE;
		END IF;
	ELSE
		DELETE FROM restcomm_media_resource_broker_entity WHERE conference_sid=in_sid AND slave_ms_id=in_slave_ms_id;
		IF NOT(SELECT master_present FROM restcomm_conference_detail_records WHERE sid=in_sid) THEN
			IF NOT EXISTS(SELECT restcomm_media_resource_broker_entity.conference_sid,restcomm_media_resource_broker_entity.slave_ms_id,restcomm_media_resource_broker_entity.slave_ms_bridge_ep_id,restcomm_media_resource_broker_entity.slave_ms_cnf_ep_id,restcomm_media_resource_broker_entity.is_bridged_together FROM restcomm_media_resource_broker_entity WHERE conference_sid=in_sid ) THEN
				UPDATE restcomm_conference_detail_records SET status=in_status,date_updated=in_date_updated WHERE sid=in_sid;
				SET completed=TRUE;
			END IF;
		END IF;
	END IF;
COMMIT;

END //
DELIMITER ;
