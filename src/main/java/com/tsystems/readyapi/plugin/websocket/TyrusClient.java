package com.tsystems.readyapi.plugin.websocket;

import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.SslContextConfigurator;
import org.glassfish.tyrus.client.SslEngineConfigurator;
import org.glassfish.tyrus.client.auth.Credentials;
import org.glassfish.tyrus.core.WebSocketException;

import com.btr.proxy.selector.direct.NoProxySelector;
import com.eviware.soapui.SoapUI;
import com.eviware.soapui.SoapUISystemProperties;
import com.eviware.soapui.model.settings.Settings;
import com.eviware.soapui.settings.SSLSettings;
import com.eviware.soapui.support.StringUtils;

public class TyrusClient extends Endpoint implements Client {
    private final static Logger LOGGER = Logger.getLogger(PluginConfig.LOGGER_NAME);

    private AtomicReference<Session> session = new AtomicReference<Session>();
    private AtomicReference<Throwable> throwable = new AtomicReference<Throwable>();
    private Queue<Message<?>> messageQueue = new LinkedBlockingQueue<Message<?>>();
    private AtomicReference<Future<?>> future = new AtomicReference<Future<?>>();

    private ClientEndpointConfig cec;
    private ClientManager client;
    private URI uri;
    private ProxySelector proxySelector;
    private boolean proxySelectorWorkaround;

    public TyrusClient(ExpandedConnectionParams connectionParams) throws URISyntaxException {

        ClientEndpointConfig.Builder builder = ClientEndpointConfig.Builder.create();

        if (connectionParams.hasSubprotocols())
            builder.preferredSubprotocols(Arrays.asList(connectionParams.subprotocols.split(",")));
        cec = builder.build();

        ClientManager client = ClientManager
                .createClient("org.glassfish.tyrus.container.jdk.client.JdkClientContainer");
        client.setAsyncSendTimeout(-1);
        client.setDefaultMaxSessionIdleTimeout(-1);

        client.getProperties().put(ClientProperties.HANDSHAKE_TIMEOUT, Integer.MAX_VALUE);
        client.getProperties().put(ClientProperties.REDIRECT_ENABLED, Boolean.TRUE);
        if (LOGGER.isTraceEnabled())
            client.getProperties().put(ClientProperties.LOG_HTTP_UPGRADE, Boolean.TRUE);

        if (connectionParams.hasCredentials())
            client.getProperties().put(
                    ClientProperties.CREDENTIALS,
                    new Credentials(connectionParams.login, connectionParams.password == null ? ""
                            : connectionParams.password));

        Settings settings = SoapUI.getSettings();

        String keyStoreUrl = System.getProperty(SoapUISystemProperties.SOAPUI_SSL_KEYSTORE_LOCATION,
                settings.getString(SSLSettings.KEYSTORE, null));

        String pass = System.getProperty(SoapUISystemProperties.SOAPUI_SSL_KEYSTORE_PASSWORD,
                settings.getString(SSLSettings.KEYSTORE_PASSWORD, ""));

        if (!StringUtils.isNullOrEmpty(keyStoreUrl)) {
            SslContextConfigurator sslContextConfigurator = new SslContextConfigurator(true);
            sslContextConfigurator.setKeyStoreFile(keyStoreUrl);
            sslContextConfigurator.setKeyStorePassword(pass);
            sslContextConfigurator.setKeyStoreType("JKS");
            if (sslContextConfigurator.validateConfiguration()) {
                SslEngineConfigurator sslEngineConfigurator = new SslEngineConfigurator(sslContextConfigurator, true,
                        false, false);
                client.getProperties().put(ClientProperties.SSL_ENGINE_CONFIGURATOR, sslEngineConfigurator);
            } else
                LOGGER.warn("error validating keystore configuration");
        }

        this.client = client;

        uri = new URI(connectionParams.getNormalizedServerUri());
    }

    /**
     * Explain why this is overridden.
     * 
     * @see com.tsystems.readyapi.plugin.websocket.Client#cancel()
     */
    @Override
    public void cancel() {
        Future<?> future;
        if ((future = this.future.get()) != null)
            future.cancel(true);
    }

    /**
     * Explain why this is overridden.
     * 
     * @see com.tsystems.readyapi.plugin.websocket.Client#connect()
     */
    @Override
    public void connect() {
        if (isConnected())
            return;
        try {
            checkProxySelector();

            throwable.set(null);
            future.set(null);

            Future<Session> future = client.asyncConnectToServer(this, cec, uri);
            this.future.set(future);

        } catch (Exception e) {
            Throwable th = ExceptionUtils.getRootCause(e);
            throwable.set(th != null ? th : e);
            SoapUI.logError(th != null ? th : e);
        }
    }

    // FIXME: workaround https://java.net/jira/browse/TYRUS-412
    public void checkProxySelector() {
        proxySelector = ProxySelector.getDefault();
        if (proxySelector == null) {
            proxySelectorWorkaround = true;
            ProxySelector.setDefault(NoProxySelector.getInstance());
        }
    }

    /**
     * Explain why this is overridden.
     * 
     * @see com.tsystems.readyapi.plugin.websocket.Client#disconnect(boolean)
     */
    @Override
    public void disconnect(boolean harshDisconnect) throws Exception {
        Session session;
        if ((session = this.session.get()) != null)
            if (!harshDisconnect)
                session.close(new CloseReason(CloseCodes.NORMAL_CLOSURE, "drop connection test step"));
            else {
                session.close(new CloseReason(CloseCodes.PROTOCOL_ERROR, "drop connection test step"));
                this.session.set(null);
            }
    }

    /**
     * Explain why this is overridden.
     * 
     * @see com.tsystems.readyapi.plugin.websocket.Client#dispose()
     */
    @Override
    public void dispose() {
        resetProxySelector();

        try {
            Session session;
            if ((session = this.session.get()) != null)
                session.close();
            this.session.set(null);
            throwable.set(null);
            future.set(null);
        } catch (Exception e) {
            LOGGER.error(e);
        }
    }

    /**
     * Explain why this is overridden.
     * 
     * @see com.tsystems.readyapi.plugin.websocket.Client#getMessageQueue()
     */
    @Override
    public Message<?> nextMessage() {
        return messageQueue.poll();
    }

    /**
     * Explain why this is overridden.
     * 
     * @see com.tsystems.readyapi.plugin.websocket.Client#getThrowable()
     */
    @Override
    public Throwable getThrowable() {
        return throwable.get();
    }

    /**
     * Explain why this is overridden.
     * 
     * @see com.tsystems.readyapi.plugin.websocket.Client#isAvailable()
     */
    @Override
    public boolean isAvailable() {
        Future<?> future;
        if ((future = this.future.get()) != null)
            if (future.isDone())
                try {
                    future.get();
                    return true;
                } catch (Exception e) {
                    throwable.set(e);
                    return false;
                }
            else
                return false;
        else
            return true;
    }

    /**
     * Explain why this is overridden.
     * 
     * @see com.tsystems.readyapi.plugin.websocket.Client#isConnected()
     */
    @Override
    public boolean isConnected() {
        Session session;
        if ((session = this.session.get()) != null)
            return session.isOpen();
        else
            return false;
    }

    /**
     * Explain why this is overridden.
     * 
     * @see com.tsystems.readyapi.plugin.websocket.Client#isFaulty()
     */
    @Override
    public boolean isFaulty() {
        return throwable.get() != null;
    }

    @Override
    public void onClose(Session session, final CloseReason closeReason) {
        SoapUI.log("WebSocketClose statusCode=" + closeReason.getCloseCode() + " reason="
                + closeReason.getReasonPhrase());
        messageQueue.clear();

        resetProxySelector();

        this.session.set(null);

        Future<?> future;
        if (closeReason.getCloseCode().getCode() > CloseCodes.NORMAL_CLOSURE.getCode())
            throwable.set(websocketException("Websocket connection closed abnormaly.", closeReason));
        else if ((future = this.future.get()) != null)
            if (!future.isDone())
                throwable.set(websocketException("Websocket connection closed unexpected.", closeReason));
    }

    public WebSocketException websocketException(final String message, final CloseReason closeReason) {
        return new WebSocketException(message) {

            @Override
            public CloseReason getCloseReason() {
                return closeReason;
            }

            @Override
            public String toString() {
                return getMessage()
                        + " ["
                        + closeReason.getCloseCode().getCode()
                        + "] "
                        + closeReason.getCloseCode()
                        + (StringUtils.hasContent(closeReason.getReasonPhrase()) ? " '" + closeReason.getReasonPhrase()
                                + "' " : "");
            }
        };
    }

    // FIXME: workaround https://java.net/jira/browse/TYRUS-412
    public void resetProxySelector() {
        if (proxySelectorWorkaround)
            ProxySelector.setDefault(proxySelector);
    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        SoapUI.log("WebSocketConnect success=" + session.isOpen() + " accepted protocol="
                + session.getNegotiatedSubprotocol());
        session.addMessageHandler(new MessageHandler.Whole<String>() {

            @Override
            public void onMessage(String payload) {
                Message.TextMessage message = new Message.TextMessage(payload);
                messageQueue.offer(message);

            }
        });
        session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {

            @Override
            public void onMessage(ByteBuffer payload) {
                Message.BinaryMessage message = new Message.BinaryMessage(payload);
                messageQueue.offer(message);
            }
        });
        this.session.set(session);

        resetProxySelector();

    }

    @Override
    public void onError(Session session, Throwable cause) {
        SoapUI.logError(cause, "WebSocketError");

        resetProxySelector();

        throwable.set(cause);
    }

    /**
     * Explain why this is overridden.
     * 
     * @see com.tsystems.readyapi.plugin.websocket.Client#sendMessage(com.tsystems.readyapi.plugin.websocket.Message)
     */
    @Override
    public void sendMessage(Message<?> message) {
        Session session;
        if ((session = this.session.get()) != null) {
            throwable.set(null);
            future.set(null);
            if (message instanceof Message.TextMessage) {
                Message.TextMessage text = (Message.TextMessage) message;
                future.set(session.getAsyncRemote().sendText(text.getPayload()));
            }
            if (message instanceof Message.BinaryMessage) {
                Message.BinaryMessage binary = (Message.BinaryMessage) message;
                future.set(session.getAsyncRemote().sendBinary(binary.getPayload()));
            }
        }
    }
}