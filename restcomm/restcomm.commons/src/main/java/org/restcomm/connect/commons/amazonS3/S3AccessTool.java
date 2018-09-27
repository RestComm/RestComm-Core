/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2014, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 */
package org.restcomm.connect.commons.amazonS3;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.StorageClass;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Date;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class S3AccessTool {

    private static Logger logger = Logger.getLogger(S3AccessTool.class);

    private String accessKey;
    private String securityKey;
    private String bucketName;
    private String folder;
    private String bucketRegion;
    private boolean reducedRedundancy;
    private int minutesToRetainPublicUrl;
    private boolean removeOriginalFile;
    private boolean testing;
    private String testingUrl;
    private AmazonS3 s3client;
    private int maxDelay;

    public S3AccessTool(final String accessKey, final String securityKey, final String bucketName, final String folder,
            final boolean reducedRedundancy, final int minutesToRetainPublicUrl, final boolean removeOriginalFile,
                        final String bucketRegion, final boolean testing, final String testingUrl) {
        this.accessKey = accessKey;
        this.securityKey = securityKey;
        this.bucketName = bucketName;
        this.folder = folder;
        this.reducedRedundancy = reducedRedundancy;
        this.minutesToRetainPublicUrl = minutesToRetainPublicUrl;
        this.removeOriginalFile = removeOriginalFile;
        this.bucketRegion = bucketRegion;
        this.testing = testing;
        this.testingUrl = testingUrl;
        this.maxDelay = RestcommConfiguration.getInstance().getMain().getRecordingMaxDelay();
    }

    public AmazonS3 getS3client() {
        BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKey, securityKey);

        if (testing && (!testingUrl.isEmpty() || !testingUrl.equals(""))) {
            s3client = new AmazonS3Client(awsCreds);
            s3client.setRegion(Region.getRegion(Regions.fromName(bucketRegion)));
            s3client.setEndpoint(testingUrl);
            s3client.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(true).disableChunkedEncoding().build());
        } else {
            s3client = AmazonS3ClientBuilder.standard().withRegion(Regions.fromName(bucketRegion))
                    .withCredentials(new AWSStaticCredentialsProvider(awsCreds)).build();
        }

        return s3client;
    }

    public boolean uploadFile(final String fileToUpload) {
        if (s3client == null) {
            s3client = getS3client();
        }
        if(logger.isInfoEnabled()){
            logger.info("S3 Region: "+bucketRegion.toString());
        }
        try {
            URI fileUri = URI.create(fileToUpload);
            File file = new File(fileUri);
            String bucket = prepareBucket();
            if (logger.isInfoEnabled()) {
                logger.info("File to upload to S3: " + fileUri.toString());
            }

            //For statistics and logs
            DateTime start, end;
            double waitDuration;

            if (fileExists(file)) {
                start = DateTime.now();
                PutObjectRequest putRequest = new PutObjectRequest(bucket, file.getName(), file);
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentType(new MimetypesFileTypeMap().getContentType(file));
                putRequest.setMetadata(metadata);
                if (reducedRedundancy)
                    putRequest.setStorageClass(StorageClass.ReducedRedundancy);
                s3client.putObject(putRequest);

                if (removeOriginalFile) {
                    removeLocalFile(file);
                }
                end = DateTime.now();
                waitDuration = (end.getMillis() - start.getMillis())/1000;
                if (waitDuration > maxDelay || testing) {
                    if (logger.isInfoEnabled()) {
                        String msg = String.format("File %s uploaded to S3 successfully. Upload time %,.2f sec", fileUri.toString(), waitDuration);
                        logger.info(msg);
                    }
                }
                return true;
            } else {
                String msg = String.format("Recording file \"%s\" doesn't exists ",file.getPath());
                logger.error(msg);
                return false;
            }
         } catch (AmazonServiceException ase) {
            logger.error("Caught an AmazonServiceException");
            logger.error("Error Message:    " + ase.getMessage());
            logger.error("HTTP Status Code: " + ase.getStatusCode());
            logger.error("AWS Error Code:   " + ase.getErrorCode());
            logger.error("Error Type:       " + ase.getErrorType());
            logger.error("Request ID:       " + ase.getRequestId());
            return false;
        } catch (AmazonClientException ace) {
            logger.error("Caught an AmazonClientException ");
            logger.error("Error Message: " + ace.getMessage());
            return false;
        }
    }

    private String prepareBucket () {
        StringBuffer bucket = new StringBuffer();
        bucket.append(bucketName);
        if (folder != null && !folder.isEmpty())
            bucket.append("/").append(folder);
        return bucket.toString();
    }

    private boolean fileExists(final File file) {
        if (file.exists()) {
            return true;
        } else {
            return FileUtils.waitFor(file, 2);
        }
    }

    public URI getS3Uri(final String fileToUpload) {
        if (s3client == null) {
            s3client = getS3client();
        }

        String bucket = prepareBucket();
        URI fileUri = URI.create(fileToUpload);
        File file = new File(fileUri);
        URI recordingS3Uri = null;
        try {
            recordingS3Uri = s3client.getUrl(bucket, file.getName()).toURI();
        } catch (URISyntaxException e) {
            logger.error("Problem during creation of S3 URI");
        }
        if (logger.isInfoEnabled()) {
            logger.info("Created S3Uri for file: " + fileUri.toString());
        }
        return recordingS3Uri;
    }

    public URI getPublicUrl (String fileName) throws URISyntaxException {
        if (s3client == null) {
            s3client = getS3client();
        }
        Date date = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.MINUTE, minutesToRetainPublicUrl);
        if (logger.isInfoEnabled()) {
            final String msg = String.format("Prepared amazon s3 public url valid for %s minutes for recording: %s",minutesToRetainPublicUrl, fileName);
            logger.info(msg);
        }
        date = cal.getTime();
        String bucket = prepareBucket();
        GeneratePresignedUrlRequest generatePresignedUrlRequestGET =
                new GeneratePresignedUrlRequest(bucket, fileName);
        generatePresignedUrlRequestGET.setMethod(HttpMethod.GET);
        generatePresignedUrlRequestGET.setExpiration(date);

        return s3client.generatePresignedUrl(generatePresignedUrlRequestGET).toURI();
    }

    private void removeLocalFile(final File file) {
        if (!file.delete()) {
            if(logger.isInfoEnabled()){
                logger.info("Error while trying to delete the file: "+file.toString());
            }
        }
    }

    public void removeS3Uri(String recordingSid) {
        if (s3client == null) {
            s3client = getS3client();
        }
        String bucket = prepareBucket();
        String recordingKey = recordingSid.concat(".wav");
        if (logger.isInfoEnabled()) {
            logger.info("Bucket: "+bucket+", object key to remove: "+recordingKey);
        }
        //S3 URI: https://hastaging-restcomm-as-a-service.s3.amazonaws.com/REffff84e4fa224d89b213ff25362a2cb1.wav
        s3client.deleteObject(new DeleteObjectRequest(bucket, recordingKey));
    }
}
