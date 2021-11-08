/*
 *  MIT LICENCE
 *
 *  Copyright 2017 - John Szaszvari <jszaszvari@gmail.com>
 *  Copyright 2018 - Infrabel Linux team <pieter.depraetere@infrabel.be>
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *  
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *  
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 *
 *  Many thanks too:
 *  - Hayden Bakkum: Who wrote the original Slack plugin this is based off.
 *  - The Rocket.Chat Team: For making such a great open source product
 *
 */

package com.jszaszvari.rundeck.plugins.rocketchat;

import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.PropertyScope;
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription;
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty;
import com.dtolabs.rundeck.plugins.notification.NotificationPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import org.json.*;


@Plugin(service= "Notification", name="RocketChatNotification")
@PluginDescription(title="Rocket.Chat Notification", description="Sends a Rundeck Notification to Rocket.Chat")
public class RocketChatNotificationPlugin implements NotificationPlugin {

    private static final String ROCKET_CHAT_MESSAGE_COLOR_GREEN = "good";
    private static final String ROCKET_CHAT_MESSAGE_COLOR_YELLOW = "warning";
    private static final String ROCKET_CHAT_MESSAGE_COLOR_RED = "danger";

    private static final String ROCKET_CHAT_MESSAGE_FROM_NAME = "Rundeck";
//    private static final String ROCKET_CHAT_EXT_MESSAGE_TEMPLATE_PATH = "/var/lib/rundeck/libext/templates";
    private static final String ROCKET_CHAT_MESSAGE_TEMPLATE = "rocket-chat-incoming-message.ftl";

    private static final String TRIGGER_START = "start";
    private static final String TRIGGER_SUCCESS = "success";
    private static final String TRIGGER_FAILURE = "failure";
    private static final String TRIGGER_AVGDURATION = "avgduration";

    private static final Map<String, RocketChatNotificationData> TRIGGER_NOTIFICATION_DATA = new HashMap<String, RocketChatNotificationData>();

    private static final Configuration FREEMARKER_CFG = new Configuration();

    @PluginProperty(
            title = "Rocket.Chat WebHook URL", 
            description = "URL of the Incoming WebHook/Intergration that is configured in Rocket.Chat", 
            required = true)
    private String webhook_url;
    
    @PluginProperty(
            title = "Channel",
            description = "The Rocket.Chat channel to send notification messages to.",
            required = true,
            defaultValue = "#general")
    private String room;

    @PluginProperty(
            title = "Template",
            description = "Message template.",
            required = true,
            defaultValue = ROCKET_CHAT_MESSAGE_TEMPLATE
    )
    private String message_template;

    @PluginProperty(
            title = "Message on abort",
            description = "Send a message when a job is aborted.",
            required = true,
            defaultValue = "false"
    )
    private boolean message_on_abort;

  
    public boolean postNotification(String trigger, Map executionData, Map config) {

        String ACTUAL_ROCKET_CHAT_TEMPLATE;

            ClassTemplateLoader builtInTemplate = new ClassTemplateLoader(RocketChatNotificationPlugin.class, "/templates");
            TemplateLoader[] loaders = new TemplateLoader[]{builtInTemplate};
            MultiTemplateLoader mtl = new MultiTemplateLoader(loaders);
            FREEMARKER_CFG.setTemplateLoader(mtl);
            ACTUAL_ROCKET_CHAT_TEMPLATE = message_template;

        TRIGGER_NOTIFICATION_DATA.put(TRIGGER_START,   new RocketChatNotificationData(ACTUAL_ROCKET_CHAT_TEMPLATE, ROCKET_CHAT_MESSAGE_COLOR_YELLOW));
        TRIGGER_NOTIFICATION_DATA.put(TRIGGER_SUCCESS, new RocketChatNotificationData(ACTUAL_ROCKET_CHAT_TEMPLATE, ROCKET_CHAT_MESSAGE_COLOR_GREEN));
        TRIGGER_NOTIFICATION_DATA.put(TRIGGER_FAILURE, new RocketChatNotificationData(ACTUAL_ROCKET_CHAT_TEMPLATE, ROCKET_CHAT_MESSAGE_COLOR_RED));
        TRIGGER_NOTIFICATION_DATA.put(TRIGGER_AVGDURATION, new RocketChatNotificationData(ACTUAL_ROCKET_CHAT_TEMPLATE, ROCKET_CHAT_MESSAGE_COLOR_RED));

        try {
            FREEMARKER_CFG.setSetting(Configuration.CACHE_STORAGE_KEY, "strong:20, soft:250");
        }catch(Exception e){
            System.err.printf("Got an exception from Freemarker: %s", e.getMessage());
        }

        if (!TRIGGER_NOTIFICATION_DATA.containsKey(trigger)) {
            throw new IllegalArgumentException("Unknown trigger type: [" + trigger + "].");
        }

        if (!message_on_abort && (executionData.get("status") == "aborted" || executionData.get("status") == "scheduled")) {
            return true;
        }

        String message = generateMessage(trigger, executionData, config, room);
        String rocketResponse = invokeRocketChatAPIMethod(webhook_url, message);
        String ms = "payload=" + URLEncoder.encode(message);

        JSONObject rocketResponseObj = new JSONObject(rocketResponse);
        Boolean rocketResponseStatus = rocketResponseObj.getBoolean("success");

        if (rocketResponseStatus == true) {
            return true;
        } else {
            throw new RocketChatNotificationPluginException("Unknown status returned from Rocket.Chat: [" + rocketResponse + "]." + "\n" + ms);
        }
    }

    private String generateMessage(String trigger, Map executionData, Map config, String channel) {
        String templateName = TRIGGER_NOTIFICATION_DATA.get(trigger).template;
        String color = TRIGGER_NOTIFICATION_DATA.get(trigger).color;

        HashMap<String, Object> model = new HashMap<String, Object>();
        model.put("trigger", trigger);
        model.put("color", color);
        model.put("executionData", executionData);
        model.put("config", config);
        model.put("channel", channel);

        StringWriter sw = new StringWriter();
        try {
            Template template = FREEMARKER_CFG.getTemplate(templateName);
            template.process(model,sw);

        } catch (IOException ioEx) {
            throw new RocketChatNotificationPluginException("Error loading Rocket.Chat message template: [" + ioEx.getMessage() + "].", ioEx);
        } catch (TemplateException templateEx) {
            throw new RocketChatNotificationPluginException("Error merging Rocket.Chat notification message template: [" + templateEx.getMessage() + "].", templateEx);
        }

        return sw.toString();
    }

    private String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException unsupportedEncodingException) {
            throw new RocketChatNotificationPluginException("URL encoding error: [" + unsupportedEncodingException.getMessage() + "].", unsupportedEncodingException);
        }
    }

    private String invokeRocketChatAPIMethod(String webhook_url, String message) {
        URL requestUrl = toURL(webhook_url);

        HttpURLConnection connection = null;
        InputStream responseStream = null;
        String body = "payload=" + URLEncoder.encode(message);
        try {
            connection = openConnection(requestUrl);
            putRequestStream(connection, body);
            responseStream = getResponseStream(connection);
            return getRocketChatResponse(responseStream);

        } finally {
            closeQuietly(responseStream);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private URL toURL(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException malformedURLEx) {
            throw new RocketChatNotificationPluginException("Rocket.Chat WebHook URL is malformed: [" + malformedURLEx.getMessage() + "].", malformedURLEx);
        }
    }

    private HttpURLConnection openConnection(URL requestUrl) {
        try {
            return (HttpURLConnection) requestUrl.openConnection();
        } catch (IOException ioEx) {
            throw new RocketChatNotificationPluginException("Error opening connection to Rocket.Chat WebHook URL: [" + ioEx.getMessage() + "].", ioEx);
        }
    }

    private void putRequestStream(HttpURLConnection connection, String message) {
        try {
            connection.setRequestMethod("POST");
//            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("charset", "utf-8");

            connection.setDoInput(true);
            connection.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
            wr.writeBytes(message);
            wr.flush();
            wr.close();
        } catch (IOException ioEx) {
            throw new RocketChatNotificationPluginException("Error putting data to Rocket.Chat: [" + ioEx.getMessage() + "].", ioEx);
        }
    }

    private InputStream getResponseStream(HttpURLConnection connection) {
        InputStream input = null;
        try {
            input = connection.getInputStream();
        } catch (IOException ioEx) {
            input = connection.getErrorStream();
        }
        return input;
    }

    private int getResponseCode(HttpURLConnection connection) {
        try {
            return connection.getResponseCode();
        } catch (IOException ioEx) {
            throw new RocketChatNotificationPluginException("Failed to obtain HTTP response: [" + ioEx.getMessage() + "].", ioEx);
        }
    }

    private String getRocketChatResponse(InputStream responseStream) {
        try {
            return new Scanner(responseStream,"UTF-8").useDelimiter("\\A").next();
        } catch (Exception ioEx) {
            throw new RocketChatNotificationPluginException("Error reading Rocket.Chat JSON response: [" + ioEx.getMessage() + "].", ioEx);
        }
    }

    private void closeQuietly(InputStream input) {
        if (input != null) {
            try {
                input.close();
            } catch (IOException ioEx) {
                // ignore
            }
        }
    }

    private static class RocketChatNotificationData {
        private String template;
        private String color;
        public RocketChatNotificationData(String template, String color) {
            this.color = color;
            this.template = template;
        }
    }

}
