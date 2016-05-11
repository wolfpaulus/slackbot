package com.techcasita.bot;

import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple WebApp to proxy calls to Loebner Prize Bronze Medal Winner for 2014 and 2015
 * http://www.loebner.net/Prizef/loebner-prize.html
 *
 * @author <a href="mailto:wolf@wolfpaulus.com">Wolf Paulus</a>
 */
@ApplicationPath("/*")
public class SlackConnector extends Application {
    private static final Logger Log = LoggerFactory.getLogger(SlackConnector.class);

    private static final String ROSE = "http://ec2-54-215-197-164.us-west-1.compute.amazonaws.com/ui.php";
    private static final String MITSUKU = "http://fiddle.pandorabots.com/pandora/talk-xml";
    private static final String MITSUKU_ID = "f326d0be8e345a13";
    /**
     * SlackMessagePostedListener for the Rose bot.
     */
    @SuppressWarnings({"FieldCanBeLocal", "Convert2Lambda"})
    private final SlackMessagePostedListener rose = new SlackMessagePostedListener() {
        @Override
        public void onEvent(final SlackMessagePosted event, final SlackSession session) {
            final MultivaluedMap<String, String> payload = new MultivaluedHashMap<>();
            payload.add("user", event.getSender().getId());
            payload.add("message", event.getMessageContent());
            payload.add("send", "");

            final Client client = ClientBuilder.newClient();
            final Response response = client.target(ROSE).request()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED)
                    .header(HttpHeaders.ACCEPT, MediaType.WILDCARD)
                    .post(Entity.form(payload));
            String s = "I have no idea what you are talking about.";
            if (response.getStatus() == 200) {
                s = response.readEntity(String.class);
            }
            session.sendMessageToUser(event.getSender(), s, null);
        }
    };
    /**
     * SlackMessagePostedListener for the Mitsuku bot.
     * Since the bot creates its own sessionid (aka custid) we need to keep track and map it to the slack user.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final SlackMessagePostedListener mitsuku = new SlackMessagePostedListener() {
        final Map<String, String> map = new ConcurrentHashMap<>();

        @Override
        public void onEvent(final SlackMessagePosted event, final SlackSession session) {
            final MultivaluedMap<String, String> payload = new MultivaluedHashMap<>();
            payload.add("botid", MITSUKU_ID);
            payload.add("input", event.getMessageContent());
            payload.add("custid", map.containsKey(event.getSender().getId()) ? map.get(event.getSender().getId()) : "");

            final Client client = ClientBuilder.newClient();
            final Response response = client.target(MITSUKU).request()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED)
                    .header(HttpHeaders.ACCEPT, MediaType.TEXT_XML)
                    .post(Entity.form(payload));
            String s = "I have no idea what you are talking about.";
            if (response.getStatus() == 200) {
                final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                try {
                    final Document document = factory.newDocumentBuilder().parse(response.readEntity(InputStream.class));
                    // store custid, to be re-used by this slack user.
                    map.put(event.getSender().getId(), document.getDocumentElement().getAttribute("custid"));
                    s = document.getElementsByTagName("that").item(0).getTextContent();
                } catch (ParserConfigurationException | SAXException | IOException e) {
                    Log.error(e.toString());
                }
            }
            session.sendMessageToUser(event.getSender(), s, null);
        }
    };

    /**
     * Connecting the webhooks
     */
    public SlackConnector() {
        final String TOKEN_FOR_ROSE = PROP.getAppProperty("ROSE_TOKEN", "");
        final String TOKEN_FOR_MITSUKU = PROP.getAppProperty("MITSUKU_TOKEN", "");
        final String TOKEN2_FOR_MITSUKU = PROP.getAppProperty("MITSUKU_TC", "");
        try {
            // connecting Rose to the Slack Team
            final SlackSession s1 = SlackSessionFactory.createWebSocketSlackSession(TOKEN_FOR_ROSE);
            s1.connect();
            s1.addMessagePostedListener(rose);

            // connecting Mitsuku to the Slack Team
            final SlackSession s2 = SlackSessionFactory.createWebSocketSlackSession(TOKEN_FOR_MITSUKU);
            s2.connect();
            s2.addMessagePostedListener(mitsuku);

            // connecting Mitsuku to another Slack Team
            final SlackSession s3 = SlackSessionFactory.createWebSocketSlackSession(TOKEN2_FOR_MITSUKU);
            s3.connect();
            s3.addMessagePostedListener(mitsuku);
        } catch (IOException e) {
            Log.error(e.toString());
        }
    }

    /**
     * Reads API tokens form the resources/app.properties file,
     * which obviously is not in checked-in in Git
     */
    private static class PROP {
        private static final Properties prop = new Properties();

        static {
            final ClassLoader classloader = Thread.currentThread().getContextClassLoader();
            try {
                prop.load(classloader.getResourceAsStream("app.properties"));
            } catch (IOException e) {
                Log.warn("Couldn't load properties, to read version string: " + e.toString());
            }
        }

        static String getAppProperty(final String name, final String _default) {
            return prop.getProperty(name, _default);
        }
    }
}