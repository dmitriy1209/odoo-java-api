/*
 *   Copyright 2011-2014 De Bortoli Wines Pty Limited (Australia)
 *
 *   This file is part of OdooJavaAPI.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package com.odoojava.api;

import java.util.ArrayList;
import javax.xml.bind.DatatypeConverter;
import java.util.HashMap;
import java.util.Map;
import org.apache.xmlrpc.XmlRpcException;
import com.odoojava.api.OdooXmlRpcProxy.RPCProtocol;
import com.odoojava.api.OdooXmlRpcProxy.RPCServices;
import java.util.Arrays;
import static java.util.Arrays.asList;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * *
 * Manages an Odoo session by holding context and initiating all calls to the
 * server.
 *
 * @author Pieter van der Merwe
 *
 */
public class Session {

    private static final String LINE_SEPARATOR_SYSTEM_PROPERTY = "line.separator";
    private static final String LINE_SEPARATOR = System.getProperty(LINE_SEPARATOR_SYSTEM_PROPERTY);
    private static boolean connecting = false;
    private String host;
    private int port;
    private String databaseName;
    private String userName;
    private String password;
    private int userID;
    @Getter
    private Context context = new Context();
    private RPCProtocol protocol;
    private OdooXmlRpcProxy objectClient;
//    private XmlRpcClient xmlRpcClient;
    private Version serverVersion;

    /**
     * * Session constructor
     *
     * @param host Host name or IP address where the Odoo server is hosted
     * @param port XML-RPC port number to connect to. Typically 8069.
     * @param databaseName Database name to connect to
     * @param userName Username to log into the Odoo server
     * @param password Password to log into the Odoo server
     */
    public Session(RPCProtocol protocol, String host, int port, String databaseName, String userName, String password) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.userName = userName;
        this.password = password;
        this.objectClient = new OdooXmlRpcProxy(protocol, host, port, RPCServices.RPC_OBJECT);
//        this.xmlRpcClient = new XmlRpcClient() {{
//                setConfig(new XmlRpcClientConfigImpl() {{
//                        setServerURL(new URL(String.format("%s/xmlrpc/2/object", url)));
//                    }
//                });
//            }
//        };
    }

    /**
     * * Session constructor. Uses default http protocol to connect.
     *
     * @param host Host name or IP address where the Odoo server is hosted
     * @param port XML-RPC port number to connect to. Typically 8069.
     * @param databaseName Database name to connect to
     * @param userName Username to log into the Odoo server
     * @param password Password to log into the Odoo server
     */
    public Session(String host, int port, String databaseName, String userName, String password) {
        this(RPCProtocol.RPC_HTTP, host, port, databaseName, userName, password);
    }

    /**
     * Returns an initialized ObjectAdapter object for ease of reference. A
     * ObjectAdapter object does type conversions and error checking before
     * making a call to the server
     *
     * @return
     */
    public ObjectAdapter getObjectAdapter(String objectName) throws OdooApiException {
        return new ObjectAdapter(this, objectName);
    }

    /**
     * * Starts a session on the Odoo server and saves the UserID for use in
     * later calls
     *
     * @throws Exception upon failure to connect
     */
    public void startSession() throws Exception {

        checkDatabasePresenceSafe();

        // Synchronize all threads to login. If you login with the same user at
        // the same time you get concurrency
        // errors in the Odoo server (for example by running a multi threaded
        // ETL process like Kettle).
        Session.startConnecting();
        try {
            authenticate();
            checkVersionCompatibility();
        } finally {
            Session.connecting = false;
    }
        getRemoteContext();
    }

    //changed for testing another version of ODOO (11 and 12)
    private void checkVersionCompatibility() throws OdooApiException {
        /*
        if (this.getServerVersion().getMajor() < 8 || this.getServerVersion().getMajor() > 10) {
            throw new OdooApiException(
                    "Only Odoo Version from v8.x to 10.x are maintained. " + "Please choose another version of the library");
        }
        */
    }

    /**
     *
     * @param reportName
     * @return reportAdapter initialized with
     * @throws OdooApiException
     */
    public ReportAdapter getReportAdapter(String reportName) throws OdooApiException {
        try {
            ReportAdapter reportAdapter = new ReportAdapter(this);
            reportAdapter.setReport(reportName);
            return reportAdapter;
        } catch (XmlRpcException ex) {
            throw new XmlRpcRuntimeException(ex);
        }
    }

    void getRemoteContext() {
        this.context.clear();
        @SuppressWarnings("unchecked")
        HashMap<String, Object> odooContext = (HashMap<String, Object>)
                this.executeCommand("res.users", "context_get", new Object[]{});
        this.context.putAll(odooContext);

        // Standard behavior is web/gui clients.
        this.context.setActiveTest(true);
    }

    int authenticate() throws Exception {
        OdooXmlRpcProxy commonClient = new OdooXmlRpcProxy(protocol, host, port, RPCServices.RPC_COMMON);

        Object id = commonClient.execute("login", new Object[]{databaseName, userName, password});

        if (id instanceof Integer) userID = (Integer) id;
        else throw new OdooAuthenticationException ("Incorrect username and/or password.  Login Failed.");

        return userID;
    }

    void checkDatabasePresenceSafe() {
        // 21/07/2012 - Database listing may not be enabled (--no-database-list
        // or list_db=false).
        // Only provides additional information in any case.
        try {
            checkDatabasePresence();
        } catch (Exception e) {}
            }

    void checkDatabasePresence() {
        ArrayList<String> dbList = getDatabaseList(protocol, host, port);
        if (!dbList.contains(databaseName)) {
            StringBuilder messageBuilder = new StringBuilder("Error while connecting to Odoo.  Database [")
                    .append(databaseName).append("]  was not found in the following list: ").append(LINE_SEPARATOR)
                    .append(LINE_SEPARATOR).append(String.join(LINE_SEPARATOR, dbList)).append(LINE_SEPARATOR);

            throw new IllegalStateException(messageBuilder.toString());
    }
    }

    private synchronized static void startConnecting() {
        while (Session.connecting) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
                    }
            }
        Session.connecting = true;
    }

    /**
     * * Get a list of databases available on a specific host and port with the
     * http protocol.
     *
     * @param host Host name or IP address where the Odoo server is hosted
     * @param port XML-RPC port number to connect to
     * @return A list of databases available for the Odoo instance
     */
    public static ArrayList<String> getDatabaseList(String host, int port) {
        return getDatabaseList(RPCProtocol.RPC_HTTP, host, port);
    }

    /**
     * * Get a list of databases available on a specific host and port
     *
     * @param protocol Protocol to use when connecting to the RPC service ex.
     * http/https
     * @param host Host name or IP address where the Odoo server is hosted
     * @param port XML-RPC port number to connect to
     * @return A list of databases available for the Odoo instance
     */
    public static ArrayList<String> getDatabaseList(RPCProtocol protocol, String host, int port) {
        try {
            OdooXmlRpcProxy client = new OdooXmlRpcProxy(protocol, host, port, RPCServices.RPC_DATABASE);
            // Retrieve databases
            return Arrays.stream(((Object[]) client.execute("list", new Object[]{})))
                    .map(e -> String.valueOf(e))
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (XmlRpcException ex) {
            throw new XmlRpcRuntimeException(ex);
        }
    }

    /**
     * Executes any command on the server linked to the /xmlrpc/object service.
     * All parameters are prepended by: "databaseName,userID,password" This
     * method execute the command without the context parameter Its purpose is
     * to be used by Odoo version prior to v10 or for v10 methods that mustn't
     * use the context
     *
     * @param objectName Object or model name to execute the command on
     * @param commandName Command name to execute
     * @param parameters List of parameters for the command. For easy of use,
     * consider the OdooCommand object or ObjectAdapter
     * @return The result of the call
     */
    public Object executeCommand(final String objectName, final String commandName, final Object[] parameters) {
        try {
            Object[] connectionParams = new Object[]{databaseName, userID, password, objectName, commandName};

            // Combine the connection parameters and command parameters
            Object[] params = new Object[connectionParams.length + (parameters == null ? 0 : parameters.length)];
            System.arraycopy(connectionParams, 0, params, 0, connectionParams.length);

            if (parameters != null && parameters.length > 0) {
                System.arraycopy(parameters, 0, params, connectionParams.length, parameters.length);
    }
            return objectClient.execute("execute", params);
        } catch (XmlRpcException ex) {
            throw new XmlRpcRuntimeException(ex);
        }
    }

    public Object executeCommand(final String objectName, final String commandName, final Object[] parameters, final String command_name) {
        try {
            final Object[] connectionParams = {this.databaseName, this.userID, this.password, objectName, commandName};
            final Object[] params = new Object[connectionParams.length + ((parameters == null) ? 0 : parameters.length)];
            System.arraycopy(connectionParams, 0, params, 0, connectionParams.length);
            if (parameters != null && parameters.length > 0) {
                System.arraycopy(parameters, 0, params, connectionParams.length, parameters.length);
            }
            return this.objectClient.execute(command_name, params);
        } catch (XmlRpcException ex) {
            throw new XmlRpcRuntimeException((Throwable) ex);
        }
    }

    public Object executeCommandRead(final String objectName, final Object[] fields, final Object [] ids) throws XmlRpcException {
        return objectClient.execute("execute_kw", asList(
                databaseName, userID, password,
                objectName, "read",
                asList(asList(ids)),
                new HashMap(3) {{
                    put("fields", fields);
                    put("context", getContext());
                    put("load", Boolean.FALSE);
            }}
        ));
    }

    /**
     * Executes any command on the server linked to the /xmlrpc/object service.
     * parameters and Context are prepended .The context MUST NOT have been
     * already passed in the parameters.
     *
     * @param objectName Object or model name to execute the command on
     * @param commandName Command name to execute
     * @param parameters List of parameters for the command. For easy of use,
     * consider the OdooCommand object or ObjectAdapter
     * @return The result of the call
     */
    public Object executeCommandWithContext(final String objectName, final String commandName,
            final Object[] parameters) {
        // Combine the parameters with the context
        Object[] params = new Object[1 + (parameters == null ? 0 : parameters.length)];
        if (parameters != null && parameters.length > 0) {
            System.arraycopy(parameters, 0, params, 0, parameters.length);
        }
        System.arraycopy(new Object[]{getContext()}, 0, params, parameters.length, 1);
        return executeCommand(objectName, commandName, params);
    }

    /**
     * Executes a workflow by sending a signal to the workflow engine for a
     * specific object. This functions calls the 'exec_workflow' method on the
     * object All parameters are prepended by: "databaseName,userID,password"
     *
     * @param objectName Object or model name to send the signal for
     * @param signal Signal name to send, for example order_confirm
     * @param objectID Specific object ID to send the signal for
     */
    public void executeWorkflow(final String objectName, final String signal, final int objectID) {        
        try {
            Object[] params = new Object[]{databaseName, userID, password, objectName, signal, objectID};
            objectClient.execute("exec_workflow", params);
        } catch (XmlRpcException ex) {
            throw new XmlRpcRuntimeException(ex);
        }
    }

    /**
     * Returns the Odoo server version for this session
     *
     * @return
     */

    public Version getServerVersion() {
        try {
            // Cache server version
            if (serverVersion == null) serverVersion = OdooXmlRpcProxy.getServerVersion(protocol, host, port);
            return serverVersion;
        } catch (XmlRpcException ex) {
            throw new XmlRpcRuntimeException(ex);
    }
    }

    public byte[] executeReportService(String reportName, Object[] ids) throws XmlRpcRuntimeException {
        try {
            if (getServerVersion().getMajor() < 11) {
                OdooXmlRpcProxy client = new OdooXmlRpcProxy(protocol, host, port, RPCServices.RPC_REPORT);
                Object[] reportParams = new Object[]{databaseName, userID, password, reportName, ids};
                Map<String, Object> result = (Map<String, Object>) client.execute("render_report", reportParams);
                return DatatypeConverter.parseBase64Binary((String) result.get("result"));
            }
            else {
                // Implement changes thanks to
                // https://github.com/OCA/odoorpc/issues/20
                return null;
            }
        } catch (XmlRpcException ex) {
            throw new XmlRpcRuntimeException(ex);
        }
    }

    /**
     * Returns the current logged in User's UserID
     *
     * @return
     */
    public int getUserID() {
        return userID;
    }

}
