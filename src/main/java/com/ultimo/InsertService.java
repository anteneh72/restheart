package com.ultimo;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import org.bson.types.ObjectId;
import org.restheart.db.MongoDBClientSingleton;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.METHOD;
import org.restheart.handlers.applicationlogic.ApplicationLogicHandler;
import org.restheart.handlers.collection.PostCollectionHandler;
import org.restheart.security.handlers.IAuthToken;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientException;
import com.mongodb.MongoException;
import com.mongodb.util.JSON;
import com.mongodb.util.JSONParseException;

import java.io.ByteArrayInputStream;

import org.apache.commons.fileupload.MultipartStream;

public class InsertService extends ApplicationLogicHandler implements IAuthToken {

	private static final Logger LOGGER = LoggerFactory.getLogger("org.restheart");
	
	public InsertService(PipedHttpHandler next, Map<String, Object> args) {
		super(next, args);
 
	}

	public void handleRequest(HttpServerExchange exchange,RequestContext context) throws Exception 
	{	

		 if (context.getMethod() == METHOD.OPTIONS) 
	        {
	            exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET");
	            exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge, " + AUTH_TOKEN_HEADER + ", " + AUTH_TOKEN_VALID_HEADER + ", " + AUTH_TOKEN_LOCATION_HEADER);
	            exchange.setResponseCode(HttpStatus.SC_OK);
	            exchange.endExchange();
	        } 
		 else if (context.getMethod() == METHOD.POST)
		 {
			try
			{
				
				/*
				 * Declare the Variables that will be used in this Insert Service. 
				 */
				String payload = "";
				String delimiter = "";
				String audit = "";
				String auditHeaders="";
				String payloadHeaders="";
				String payloadContentType = "";
				/*
				 * Read the Payload of the Incoming Request, and find the delimiter.
				 */
				InputStream input = exchange.getInputStream();
				BufferedReader inputReader = new BufferedReader(new InputStreamReader(input));
				int i = 0;
				readLoop : while(true)
						{
							
							String inputTemp = inputReader.readLine();
							if (inputTemp!= null)
							{
								if (i == 0)
								{
									delimiter = (inputTemp.split(";"))[1];
									payload = payload + inputTemp + "\r\n";
								}
								else 
								{
									payload = (payload + inputTemp + "\r\n");
								}
								i++;
							}
							else 
							{
								break readLoop;
							}
						}
				delimiter = delimiter.split("=")[1]; 
				//=============================================================================================================================================
				/*
				 * Read and Parse Multipart/Mixed Message.     
				 */
				    byte[] boundary = delimiter.getBytes();
				    byte[] contents = payload.getBytes();
			        ByteArrayInputStream content = new ByteArrayInputStream(contents);
			        MultipartStream multipartStream = new MultipartStream(content, boundary,1000, null);	        
			        boolean nextPart = multipartStream.skipPreamble();
			        int m = 0;
			        while (nextPart)
			        {
			        	
			        	if (m==0)
			        	{ 	
					            ByteArrayOutputStream body = new ByteArrayOutputStream();
					           auditHeaders = multipartStream.readHeaders();
					            multipartStream.readBodyData(body);
					             audit = new String(body.toByteArray());
						        LOGGER.info("Audit Recieved");
						        LOGGER.trace(("Audit Headers: "+ auditHeaders));
						        LOGGER.trace("Audit Body: + " + audit);

	
		       
			        	}
			        	else if(m==1)
			        	{
				            payloadHeaders = multipartStream.readHeaders();
				            System.out.println(payloadHeaders);
					            ByteArrayOutputStream body = new ByteArrayOutputStream();

					            multipartStream.readBodyData(body);
					            payload = new String(body.toByteArray());
						        LOGGER.info("Payload Recieved");
						        LOGGER.trace(("Payload Headers: "+ payloadHeaders));
						        LOGGER.trace("Payload Body: + " + payload);

	
			        	}
			           nextPart = multipartStream.readBoundary();
			           m++;
			        }
			        audit = audit.replace("\r\n", "");
			        payload = payload.replace("\r\n", "");
			        String[] pHeaders = payloadHeaders.split("\n");
			        
					//=============================================================================================================================================
					/*
					 * Find out the content type of the Payload Headers.     
					 */

			        for (String headerPart : pHeaders)
			        {
			        	
			        	String [] line = headerPart.split(";");
			        	for (String linePart : line)
			        	{
			        		if (linePart.contains("Content-Type") || linePart.contains("content-type") | linePart.contains("Content-type") | linePart.contains("content-Type") )
				        	{
				        		String[] contentType = linePart.split(":");
				        		payloadContentType = contentType[1].trim().replace(";", "");
				        	}			        	}
			        }
					//=============================================================================================================================================
					/*
					 * Insert the Payload based upon the Content Type. The Payload will be Converted in PayloadService    
					 */

			       DBObject payloadInput = null;
			       try{
				        if (payloadContentType.equalsIgnoreCase("application/xml"))
				        {
				        	payloadInput = PayloadService.payloadtoJSON(payload, "application/xml", exchange, context);
				        }
				        else if (payloadContentType.equalsIgnoreCase("application/json"))
				        {
				        	payloadInput = PayloadService.payloadtoJSON(payload, "application/json", exchange, context);
				        }
				        else if (payloadContentType.equalsIgnoreCase("text/plain"))
				        {
				        	payloadInput = PayloadService.payloadtoJSON(payload, "text/plain", exchange, context);
				        }
				        else 
				        {
				        	LOGGER.error("Not Acceptable Input");
							 throw new PayloadConversionException();
				        }
			       }
			       
			       /*
			        * If there is an error in conversion, then the payload will be saved as a text/plain payload. Conversion will happen in the PayloadService
			        */
			       catch(PayloadConversionException e)
			       {
			    	   LOGGER.error("Payload was Saved in text/plain format." );
			        	payloadInput = PayloadService.payloadtoJSON(payload, "text/plain", exchange, context);
			       }
			       
			       // Decide a ObjectID fro the payload. 
			       
			        LOGGER.trace("Payload: " + payload);
			        ObjectId id = new ObjectId();
			        LOGGER.trace("Payload Object ID: " + id.toString());
			        
			        //Insert the Payload in to the Payload Collection. 
			        
					String status = payloadInsert(id, payloadInput, context, exchange);
					LOGGER.debug("Payload Insert: " + status);
					
					if (status.equalsIgnoreCase("Success"))
					{
						
						LOGGER.trace("Audit to be inserted" + audit);
	
						DBObject inputDBObject = (DBObject) JSON.parse(audit);
						
						
				     //If the payload insert is successful, then insert the audit. 
	
						String auditInsertStatus = auditInsert(id, inputDBObject, context, exchange);
						if (auditInsertStatus == "Success")
						{
							LOGGER.debug("Audit Inserted Successfully");
						}
						else 
						{
							LOGGER.debug("Audit Insert Failed");
						}
					}
					
				}    
			
			catch(JSONParseException e) 
		    {
		    	LOGGER.error("Incorrectly Formated JSON Object. Please check JSON Object Format");
		    	
		        ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "Incorrectly Formatted JSON Object. Please check JSON Object Format");
		    }
		 }
		 else
			{
				 ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED, "Method Not Allowed");
				 exchange.endExchange();

			}
            
	}
	
	
	protected static String insertService (HttpServerExchange exchange, RequestContext context)
	{
		return null;
		
	}
	
	
	private static String auditInsert (ObjectId referenceID , DBObject inputObject, RequestContext context,HttpServerExchange exchange) throws Exception
	{
		String status = "";
	    try{
		       /*
		        * Steps for Audit Insert
		        *  1) Add Payload ID to Reference ID
		        *  2) Check if Audit has a timestamp or not. 
		        *  3) Change the Format of the Date from String to a Date Object. 
		        *  4) Update the timstamp in the payload object. 
		        *  5) Inser the Update. 
		        */
	     
				 inputObject.removeField("dataLocation");
			     inputObject.put("dataLocation", referenceID.toString());
		         if (!inputObject.containsField("timestamp"))
		         {
		        	 LOGGER.debug("Audit does not contain timestamp");
		         }
		         String timestamp =  inputObject.get("timestamp").toString();
		 	     Date gtDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(timestamp);
		 	     System.out.println(gtDate.toString());
		         inputObject.removeField("timestamp");
		         inputObject.put("timestamp",gtDate);
			     context.setContent(inputObject);
			     PostCollectionHandler handler = new PostCollectionHandler();
			     handler.handleRequest(exchange, context);
			     status = "Success";
	    	}
	    catch(	java.text.ParseException e) 
		   {
		    	LOGGER.error("Date Not Correctly Formatted. Date Format is: YYYY-MM-DDTHH:MM:SS");
		    	
		        ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "Incorrectly Formatted Date. Accepted Date Format is: YYYY-MM-DDTHH:MM:SS");
			    MongoClient client = getMongoConnection(exchange, context);
			    DB db = client.getDB(context.getDBName());
			    DBCollection collection = db.getCollection("payloadCollection");
			    DBObject removalObject = new BasicDBObject("_id", referenceID);
			    collection.remove(removalObject);
			     status = "Failed";


		
		   }
	    catch(IllegalArgumentException e) 
		   {
		    	LOGGER.error("Date Not Correctly Formatted. Date Format is: YYYY-MM-DDTHH:MM:SS");
		    	
		        ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "Incorrectly Formatted JSON Array. Please check JSON Array Format");
			    MongoClient client = getMongoConnection(exchange, context);
			    DB db = client.getDB(context.getDBName());
			    DBCollection collection = db.getCollection("payloadCollection");
			    DBObject removalObject = new BasicDBObject("_id", referenceID);
			    collection.remove(removalObject);
			     status = "Failed";

		
		   }
		 catch(JSONParseException e) 
			   {
			    	LOGGER.error("Incorrectly Formated JSON Array. Please check JSON Array Format");
			    	
			        ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "Incorrectly Formatted JSON Array. Please check JSON Array Format");
				    MongoClient client = getMongoConnection(exchange, context);
				    DB db = client.getDB(context.getDBName());
				    DBCollection collection = db.getCollection("payloadCollection");
				    DBObject removalObject = new BasicDBObject("_id", referenceID);
				    collection.remove(removalObject);
				     status = "Failed";

			
			   }
			  
	    catch(MongoClientException e)
	    {
	    	LOGGER.error("MongoDB Client Error. Ensure that DB and Collection exist");
	    	LOGGER.error(e.getMessage());
	    	status = "Failed";
	    	MongoClient client = getMongoConnection(exchange, context);
		    DB db = client.getDB(context.getDBName());
		    DBCollection collection = db.getCollection("payloadCollection");
		    DBObject removalObject = new BasicDBObject("_id", referenceID);
		    collection.remove(removalObject);
	        ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, "MongoDB Client Exception. Please check MongoDB Status");
		    status = "Failed";

	    }
		catch(MongoException e)
	    {
	    	LOGGER.error("General MongoDB Error. Please check MongoDB Connection and Permissions");
	    	LOGGER.error(e.getMessage());
	    	status = "Failed";
	    	MongoClient client = getMongoConnection(exchange, context);
		    DB db = client.getDB(context.getDBName());
		    DBCollection collection = db.getCollection("payloadCollection");
		    DBObject removalObject = new BasicDBObject("_id", referenceID);
		    collection.remove(removalObject);
	        ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, "General MongoDB Error. Please check MongoDB Connection and Permissions");
		     status = "Failed";

	    }
	
	    catch(Exception e) 
	    {
	    	LOGGER.error("Unspecified Application Error" );
	    	MongoClient client = getMongoConnection(exchange, context);
		    DB db = client.getDB(context.getDBName());
		    DBCollection collection = db.getCollection("payloadCollection");
		    DBObject removalObject = new BasicDBObject("_id", referenceID);
		    collection.remove(removalObject);
	    	status = "Failed";
	        ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, "Unspecified Application Error");
	        status = "Failed";

	    }
			     return status;		
			
	       
 	}
	
	private static String payloadInsert(ObjectId id, DBObject inputObject, RequestContext context, HttpServerExchange exchange) throws Exception
	{
		/*
		 *  Get MongoDB connection and insert the converted payload document. 
		 */
		String status = "";
		try {
		    inputObject.put("_id", id);
		    MongoClient client = getMongoConnection(exchange, context);
		    DB db = client.getDB(context.getDBName());
		    DBCollection collection = db.getCollection("payloadCollection");
		    collection.insert(inputObject);
		    status = "Success";
		}
	    catch(MongoClientException e)
	    {
	    	LOGGER.error("MongoDB Client Error. Ensure that DB and Collection exist");
	    	LOGGER.error(e.getMessage());
	    	status = "Failed";

	        ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, "MongoDB Client Exception. Please check MongoDB Status");
	
	    }
		catch(MongoException e)
	    {
	    	LOGGER.error("General MongoDB Error. Please check MongoDB Connection and Permissions");
	    	LOGGER.error(e.getMessage());
	    	status = "Failed";

	        ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, "General MongoDB Error. Please check MongoDB Connection and Permissions");
	
	    }
	
	    catch(Exception e) 
	    {
	    	LOGGER.error("Unspecified Application Error" );
	
	    	status = "Failed";
	        ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, "Unspecified Application Error");

	    }
	     return status;		
	}
	
	private static MongoClient getMongoConnection(HttpServerExchange exchange,
			RequestContext context) {
		MongoClient client = MongoDBClientSingleton.getInstance().getClient();   
		return client;
			}

	
	
}
