package org.restcomm.connect.http;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorContext;
import akka.actor.UntypedActorFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.jersey.spi.resource.Singleton;
import com.thoughtworks.xstream.XStream;
import javax.annotation.PostConstruct;
import javax.mail.internet.ContentType;
import javax.mail.internet.ParseException;
import javax.servlet.ServletContext;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import static javax.ws.rs.core.MediaType.*;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import javax.ws.rs.core.SecurityContext;
import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.faulttolerance.RestcommUntypedActor;
import org.restcomm.connect.commons.patterns.Observe;
import org.restcomm.connect.commons.patterns.Observing;
import org.restcomm.connect.commons.patterns.StopObserving;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.entities.RestCommResponse;
import org.restcomm.connect.email.EmailService;
import org.restcomm.connect.email.api.EmailRequest;
import org.restcomm.connect.email.api.EmailResponse;
import org.restcomm.connect.email.api.Mail;
import org.restcomm.connect.http.converter.EmailMessageConverter;
import org.restcomm.connect.http.converter.RestCommResponseConverter;
import org.restcomm.connect.http.security.ContextUtil;
import org.restcomm.connect.identity.UserIdentityContext;


/**
 *  @author lefty .liblefty@telestax.com (Lefteris Banos)
 */
@Path("/Accounts/{accountSid}/Email/Messages")
@ThreadSafe
@Singleton
public class EmailMessagesEndpoint extends AbstractEndpoint {
    private static Logger logger = Logger.getLogger(EmailMessagesEndpoint.class);
    @Context
    protected ServletContext context;
    protected ActorSystem system;
    protected Configuration configuration;
    protected Configuration confemail;
    protected Gson gson;
    protected AccountsDao accountsDao;
    ActorRef mailerService = null;
    protected XStream xstream;

    // Send the email.
    protected Mail emailMsg;




    public EmailMessagesEndpoint() {
        super();
    }

    @PostConstruct
    public void init() {
        final DaoManager storage = (DaoManager) context.getAttribute(DaoManager.class.getName());
        configuration = (Configuration) context.getAttribute(Configuration.class.getName());
        confemail=configuration.subset("smtp-service");
        configuration = configuration.subset("runtime-settings");
        accountsDao = storage.getAccountsDao();
        system = (ActorSystem) context.getAttribute(ActorSystem.class.getName());
        super.init(configuration);
        final EmailMessageConverter converter = new EmailMessageConverter(configuration);
        final GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Mail.class, converter);
        builder.setPrettyPrinting();
        gson = builder.create();
        xstream = new XStream();
        xstream.alias("RestcommResponse", RestCommResponse.class);
        xstream.registerConverter(converter);
        xstream.registerConverter(new RestCommResponseConverter(configuration));
    }

    private void normalize(final MultivaluedMap<String, String> data) throws IllegalArgumentException {
        final String from = data.getFirst("From");
        data.remove("From");
        try {
            data.putSingle("From", validateEmail(from));
        } catch (final InvalidEmailException  exception) {
            throw new IllegalArgumentException(exception);
        }
        final String to = data.getFirst("To");
        data.remove("To");
        try {
            data.putSingle("To", validateEmail(to));
        } catch (final InvalidEmailException exception) {
            throw new IllegalArgumentException(exception);
        }

        final String subject = data.getFirst("Subject");
        if (subject.length() > 160) {
            data.remove("Subject");
            data.putSingle("Subject", subject.substring(0, 160));
        }

        if (data.containsKey("Type")) {
            final String contentType = data.getFirst("Type");
            try {
                ContentType cType = new ContentType(contentType);
                if (cType.getParameter("charset") == null) {
                    cType.setParameter("charset", "UTF-8");
                }
                data.remove("Type");
                data.putSingle("Type", cType.toString());
            }
            catch (ParseException exception) {
                throw new IllegalArgumentException(exception);
            }
        }

        if (data.containsKey("CC")) {
            final String cc = data.getFirst("CC");
            data.remove("CC");
            try {
                data.putSingle("CC", validateEmails(cc));
            } catch (final InvalidEmailException exception) {
                throw new IllegalArgumentException(exception);
            }
        }

        if (data.containsKey("BCC")) {
            final String bcc = data.getFirst("BCC");
            data.remove("BCC");
            try {
                data.putSingle("BCC", validateEmails(bcc));
            } catch (final InvalidEmailException exception) {
                throw new IllegalArgumentException(exception);
            }
        }

    }

    @SuppressWarnings("unchecked")
    protected Response putEmailMessage(final String accountSid,
            final MultivaluedMap<String, String> data,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        permissionEvaluator.secure(accountsDao.getAccount(accountSid),
                "RestComm:Create:EmailMessages",
                userIdentityContext); //need to fix for Emails.
        try {
            validate(data);
            normalize(data);
        } catch (final RuntimeException exception) {
            return status(BAD_REQUEST).entity(exception.getMessage()).build();
        }
        final String sender = data.getFirst("From");
        final String recipient = data.getFirst("To");
        final String body = data.getFirst("Body");
        final String subject = data.getFirst("Subject");
        final String type = data.containsKey("Type") ? data.getFirst("Type") : "text/plain";
        final String cc = data.containsKey("CC")?data.getFirst("CC"):" ";
        final String bcc = data.containsKey("BCC")?data.getFirst("BCC"):" ";

        try {

            // Send the email.
            emailMsg = new Mail(sender, recipient, subject, body ,cc, bcc, DateTime.now(), accountSid, type);
            if (mailerService == null){
                mailerService = session(confemail);
            }

            final ActorRef observer = observer();
            mailerService.tell(new Observe(observer), observer);
            if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(emailMsg), APPLICATION_JSON).build();
            } else if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(emailMsg);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else {
                return null;
            }

        } catch (final Exception exception) {
            return status(INTERNAL_SERVER_ERROR).entity(exception.getMessage()).build();
        }
    }


    private void validate(final MultivaluedMap<String, String> data) throws NullPointerException {
        if (!data.containsKey("From")) {
            throw new NullPointerException("From can not be null.");
        } else if (!data.containsKey("To")) {
            throw new NullPointerException("To can not be null.");
        } else if (!data.containsKey("Body")) {
            throw new NullPointerException("Body can not be null.");
        } else if (!data.containsKey("Subject")) {
            throw new NullPointerException("Subject can not be null.");
        }
    }


    public String validateEmail(String email) throws InvalidEmailException {
        String ePattern = "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\])|(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z]{2,}))$";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(ePattern);
        java.util.regex.Matcher m = p.matcher(email);
        if (!m.matches()) {
            String err = "Not a Valid Email Address";
            throw new InvalidEmailException(err);
        }
        return email;
    }

    public String validateEmails(String emails) throws InvalidEmailException {
        String ePattern = "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\])|(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z]{2,}))$";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(ePattern);

        if (emails.indexOf(',') > 0) {
            String[] emailsArray = emails.split(",");
            for (int i = 0; i < emailsArray.length; i++) {
                java.util.regex.Matcher m = p.matcher(emailsArray[i]);
                if (!m.matches()) {
                    String err = "Not a Valid Email Address:" + emailsArray[i];
                    throw new InvalidEmailException(err);
                }
            }
        }else{
            java.util.regex.Matcher m = p.matcher(emails);
            if (!m.matches()) {
                String err = "Not a Valid Email Address";
                throw new InvalidEmailException(err);
            }
        }
        return emails;
    }


    private ActorRef session(final Configuration configuration) {
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;
            @Override
            public Actor create() throws Exception {
                return new EmailService(configuration);
            }
        });
        return system.actorOf(props);
    }

    private ActorRef observer() {
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public UntypedActor create() throws Exception {
                return new EmailSessionObserver();
            }
        });
        return system.actorOf(props);
    }

    private final class EmailSessionObserver extends RestcommUntypedActor {
        public EmailSessionObserver() {
            super();
        }

        @Override
        public void onReceive(final Object message) throws Exception {
            final Class<?> klass = message.getClass();
            if (EmailResponse.class.equals(klass)) {
                final EmailResponse response = (EmailResponse) message;
                if (!response.succeeded()) {
                    logger.error(
                            "There was an error while sending an email :" + response.error(),
                            response.cause());
                }

                final UntypedActorContext context = getContext();
                final ActorRef self = self();
                mailerService.tell(new StopObserving(self), null);
                context.stop(self);
            }else if (Observing.class.equals(klass)){
                mailerService.tell(new EmailRequest(emailMsg), self());
            }
        }
    }

    private static class InvalidEmailException extends Exception {
        //Parameterless Constructor
        public InvalidEmailException() {}

        //Constructor that accepts a message
        public InvalidEmailException(String message) {
            super(message);
        }
    }

    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response putEmailMessage(@PathParam("accountSid") final String accountSid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return putEmailMessage(accountSid, data, retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

}



