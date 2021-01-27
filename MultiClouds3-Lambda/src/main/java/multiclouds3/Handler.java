package multiclouds3;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;

import java.net.URI;
import java.net.URL;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Handler value: example.Handler
public class Handler implements RequestHandler<S3Event, String> {

    Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static final Logger logger = LoggerFactory.getLogger(Handler.class);

    private final String JPG_TYPE = (String) "jpg";
    private final String JPG_MIME = (String) "image/jpeg";
    private final String PNG_TYPE = (String) "png";
    private final String PNG_MIME = (String) "image/png";

    private static String subscriptionKey = "yoursubscriptionkey";
    private static String endpoint = "https://visionmatthewtcom.cognitiveservices.azure.com/";

    private static final String uriBase = endpoint + "vision/v3.0/describe";
    private static final String imageToAnalyze = "https://upload.wikimedia.org/wikipedia/commons/" + "1/12/Broadway_and_Times_Square_by_night.jpg";

    private static String firestoreendpoint = "https://firestore.googleapis.com/v1/projects/steadfast-bebop-273608/databases/(default)/documents/imagesdescribed/";

    @Override

    public String handleRequest(S3Event s3event, Context context) {

        // my test bucket and key pverwritten by s3 event read

        String srcBucket = "photobucketmatthewtcom";
        //default to override
        String srcKey="errornokey.jpg";

        String jsondescriberesult = null;

        try {
            logger.info("S3 EVENT: " + gson.toJson(s3event));

            S3EventNotification.S3EventNotificationRecord record = s3event.getRecords().get(0);
            srcBucket = record.getS3().getBucket().getName();

            // Object key may have spaces or unicode non-ASCII characters.
            srcKey = record.getS3().getObject().getUrlDecodedKey();

            // Infer the image type.
            Matcher matcher = Pattern.compile(".*\\.([^\\.]*)").matcher(srcKey);
            if (!matcher.matches()) {
                logger.info("Unable to infer image type for key " + srcKey);
                return "";
            }
            String imageType = matcher.group(1);
            if (!(JPG_TYPE.equals(imageType)) && !(PNG_TYPE.equals(imageType))) {
                logger.info("Skipping non-image " + srcKey);
                return "";
            }

            logger.info("start Microsoft congitive call block, working with image named " + srcKey + " from s3 "+ srcBucket);

            try {
                AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
                CloseableHttpClient httpClient = HttpClientBuilder.create().build();

                // Set the presigned URL to expire after one hour.
                java.util.Date expiration = new java.util.Date();
                long expTimeMillis = expiration.getTime();
                expTimeMillis += 1000 * 60 * 60;
                expiration.setTime(expTimeMillis);

                // generate a presigned url from the event key and bucket to send to Congitive services for it to process

                logger.info("building a presigned URL src Bucket and key for cognitive " + srcBucket + " srcKey " + srcKey);

                GeneratePresignedUrlRequest generatePresignedUrlRequest =
                        new GeneratePresignedUrlRequest(srcBucket, srcKey)
                                .withMethod(HttpMethod.GET)
                                .withExpiration(expiration);

                URL url = s3Client.generatePresignedUrl(generatePresignedUrlRequest);

                logger.info("Generated presigned URL " + url.toString());

                // setup to call Microsoft congitive services

                URIBuilder builder = new URIBuilder(uriBase);
                // Request parameters. All of them are optional.
                builder.setParameter("visualFeatures", "Categories,Description,Color");

                logger.info("calling endpoint uriBase " + uriBase);

               // Prepare the URI for the REST API method.
                URI uri = builder.build();
                HttpPost request = new HttpPost(uri);

                // Request headers to include key
                //TODO add key to paramterstore

                request.setHeader("Content-Type", "application/json");
                request.setHeader("Ocp-Apim-Subscription-Key", subscriptionKey);

                // replace with imageToAnalyze const to test with static URL as needed

                // Request body.
                StringEntity requestEntity =
                        new StringEntity("{\"url\":\"" + url.toString() + "\"}");
                request.setEntity(requestEntity);

               // Call the REST API method and get the response entity.

                logger.info("calling endpoint from URIBuilder.build()" + uri.toString());
                HttpResponse response = httpClient.execute(request);
                logger.info("Response object " + response.toString());

                HttpEntity entity = response.getEntity();

                if (entity != null) {
                    // Format and display the JSON response.
                    jsondescriberesult = EntityUtils.toString(entity);
                    logger.info("Describe service found the following  :\n");
                    logger.info(jsondescriberesult);
                 }

                //
                // and now call GCP FIRESTORE if describe returned a ressult returned...
                //

                if (jsondescriberesult !=null) {
                    logger.info("congitive replied ok to call gcp firestore");

                    CloseableHttpClient firestorehttpClient = HttpClientBuilder.create().build();

                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/json");
                    headers.put("X-Custom-Header", "application/json");

                    URIBuilder firestorebuilder = new URIBuilder(firestoreendpoint);

                    // Request parameters. set the object key
                    builder.setParameter("documentId", srcKey);

                    logger.info("calling endpoint for firestore " + firestoreendpoint);

                    // TODO change to authenticated endoint
                    // Prepare the URI for the REST API method.
                    URI firestoreuri = firestorebuilder.build();
                    HttpPost firestorerequest = new HttpPost(firestoreuri);

                    // Request headers.
                    firestorerequest.setHeader("Content-Type", "application/json");

                    logger.info("params for json object " + srcBucket+srcKey + " " + jsondescriberesult);

                    // create json for firestore, three children, child properties, then add to fields as json obj

                    JsonObject keyname = new JsonObject();
                    keyname.addProperty("stringValue", srcKey);
                    JsonObject urltoimage = new JsonObject();
                    urltoimage.addProperty("stringValue",srcBucket+"/"+srcKey);
                    JsonObject imagedes = new JsonObject();
                    imagedes.addProperty("stringValue", jsondescriberesult.toString());

                    JsonObject fields = new JsonObject();

                    fields.add("Name",keyname);
                    fields.add("urltoimage",urltoimage);
                    fields.add("imagedescription",imagedes);

                    JsonObject firestoreputjson = new JsonObject();
                    firestoreputjson.add("fields", fields);

                    logger.info("calling firestore post with " + firestoreputjson.toString());

                    // setup call to FIRESTORE with json in the body

                    StringEntity firestorerequestEntity =  new StringEntity(firestoreputjson.toString());
                    firestorerequest.setEntity(firestorerequestEntity);

                    HttpResponse firestoreresponse = firestorehttpClient.execute(firestorerequest);
                    logger.info("Firestore Response object " + firestoreresponse.toString());

                    HttpEntity firestoreentity = firestoreresponse.getEntity();

                    if (firestoreentity != null) {
                        // Format and display the JSON response.
                        jsondescriberesult = EntityUtils.toString(firestoreentity);
                        logger.info("Firestore returned  :\n");
                        logger.info(jsondescriberesult);
                    }
                } else {
                    logger.info("ERROR no response from Congitive to process");
                }

                // TODO exception handling
            } catch (Exception e) {
                logger.info("Failed to call API's\n");
                logger.info(e.toString());
            }

            return "MultiClouds3 completed";
 } catch (Exception e) {
             logger.info(e.getMessage());
           throw new RuntimeException(e);
         }
    }
}


//String json_string = "{\r\n  \"fields\": {\r\n    \"Name\": {\r\n      \"stringValue\": \"Freshpak Rooibos Tea 80Pk\"\r\n    },\r\n    \"description\": {\r\n\t\t\"stringValue\": \"Freshpak Rooibos Tea 80Pkkk\"\r\n\t}\r\n  }\r\n}";
