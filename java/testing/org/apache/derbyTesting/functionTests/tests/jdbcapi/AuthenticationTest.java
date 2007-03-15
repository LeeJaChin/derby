/*

   Derby - Class 
       org.apache.derbyTesting.functionTests.tests.jdbcapi.AuthenticationTest

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derbyTesting.functionTests.tests.jdbcapi;

import java.security.AccessController;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import javax.sql.DataSource;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.derby.jdbc.ClientDataSource;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.apache.derby.jdbc.EmbeddedSimpleDataSource;
import org.apache.derbyTesting.junit.BaseJDBCTestCase;
import org.apache.derbyTesting.junit.DatabasePropertyTestSetup;
import org.apache.derbyTesting.junit.JDBC;
import org.apache.derbyTesting.junit.JDBCDataSource;
import org.apache.derbyTesting.junit.TestConfiguration;

public class AuthenticationTest extends BaseJDBCTestCase {

    protected static String PASSWORD_SUFFIX = "suf2ix";
    protected static String USERS[] = 
        {"APP","dan","kreg","jeff","ames","jerry","francois","jamie","howardR"};

    protected String zeus = "\u0396\u0395\u03A5\u03A3";
    protected String apollo = "\u0391\u09A0\u039F\u039B\u039B\u039A\u0390";

    
    /** Creates a new instance of the Test */
    public AuthenticationTest(String name) {
        super(name);
    }

    /**
     * Set up the conection to the database.
     */
    public void setUp() throws  Exception {
        getConnection().setAutoCommit(false);
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("AuthenticationTest");
        suite.addTest(baseSuite("AuthenticationTest:embedded"));
        if (!JDBC.vmSupportsJSR169())
            suite.addTest(TestConfiguration.clientServerDecorator(
                baseSuite("AuthenticationTest:client")));
        return suite;
    }
    
    public static Test baseSuite(String name) {
        TestSuite suite = new TestSuite("AuthenticationTest");

        // set users at system level
        java.lang.System.setProperty("derby.user.system", "admin");
        java.lang.System.setProperty("derby.user.mickey", "mouse");
        
        // Use DatabasePropertyTestSetup decorator to set the user properties
        // required by this test (and shutdown the database for the
        // property to take effect).
        Properties props = new Properties();
        props.setProperty("derby.infolog.append", "true");
        props.setProperty("derby.debug.true", "AuthenticationTrace");

        Test test = new AuthenticationTest("testConnectShutdownAuthentication");
        test = DatabasePropertyTestSetup.builtinAuthentication(test,
            USERS, PASSWORD_SUFFIX);
        suite.addTest(new DatabasePropertyTestSetup (test, props, true));
        
        // DatabasePropertyTestSsetup uses SYSCS_SET_DATABASE_PROPERTY
        // so that is database level setting.
        test = new AuthenticationTest("testUserFunctions");
        test = DatabasePropertyTestSetup.builtinAuthentication(test,
            USERS, PASSWORD_SUFFIX);
        suite.addTest(new DatabasePropertyTestSetup (test, props, true));

        test = new AuthenticationTest("testNotFullAccessUsers");
        test = DatabasePropertyTestSetup.builtinAuthentication(test,
            USERS, PASSWORD_SUFFIX);
        suite.addTest(new DatabasePropertyTestSetup (test, props, true));
        
        test = new AuthenticationTest(
            "testChangePasswordAndDatabasePropertiesOnly");
        test = DatabasePropertyTestSetup.builtinAuthentication(test,
            USERS, PASSWORD_SUFFIX);
        suite.addTest(new DatabasePropertyTestSetup (test, props, true));

        // only part of this fixture runs with network server / client
        test = new AuthenticationTest("testGreekCharacters");
        test = DatabasePropertyTestSetup.builtinAuthentication(test,
            USERS, PASSWORD_SUFFIX);
        suite.addTest(new DatabasePropertyTestSetup (test, props, true));
        
        // This test needs to run in a new single use database as we're setting
        // a number of properties
        return TestConfiguration.singleUseDatabaseDecorator(suite);
    }

    // roughly based on old functionTests test users.sql, except that
    // test used 2 databases. Possibly that was on the off-chance that
    // a second database would not work correctly - but that will not
    // be tested now.
    public void testConnectShutdownAuthentication() throws SQLException {
        
        String dbName = TestConfiguration.getCurrent().getDefaultDatabaseName();
        
        // check connections while fullAccess (default) is set
        // note that builtinAuthentication has been set, as well as
        // authentication=true.
        
        // first try connection without user password
        assertConnectionFail(dbName);
        assertConnectionOK(dbName, "system", ("admin"));
        assertConnectionWOUPOK(dbName, "system", ("admin"));
        assertConnectionOK(dbName, "dan", ("dan" + PASSWORD_SUFFIX));
        assertConnectionWOUPOK(dbName, "dan", ("dan" + PASSWORD_SUFFIX));
        // try shutdown (but only dbo can do it)
        assertShutdownFail("2850H", dbName, "dan", ("dan" + PASSWORD_SUFFIX));
        assertShutdownWOUPFail("2850H", dbName, "dan", ("dan" + PASSWORD_SUFFIX));
        assertShutdownFail("2850H", dbName, "system", "admin");
        assertShutdownWOUPFail("2850H", dbName, "system", "admin");
        assertShutdownOK(dbName, "APP", ("APP" + PASSWORD_SUFFIX));
        
        // ensure that a password is encrypted
        Connection conn1 = openDefaultConnection(
            "dan", ("dan" + PASSWORD_SUFFIX));
        Statement stmt = conn1.createStatement();
        ResultSet rs = stmt.executeQuery(
            "values SYSCS_UTIL.SYSCS_GET_DATABASE_PROPERTY('derby.user.dan')");
        rs.next();
        assertNotSame(("dan"+PASSWORD_SUFFIX), rs.getString(1));
        conn1.commit();
        conn1.close();

        // specify full-access users.
        conn1 = openDefaultConnection("dan", ("dan" + PASSWORD_SUFFIX));
        setDatabaseProperty(
            "derby.database.fullAccessUsers", 
            "APP,system,nomen,francois,jeff", conn1);
        setDatabaseProperty(
            "derby.database.defaultConnectionMode","NoAccess", conn1);
        conn1.commit();

        // check the system wide user
        assertConnectionOK(dbName, "system", "admin"); 
        // check the non-existent, but allowed user
        assertConnectionFail("08004", dbName, "nomen", "nescio");
        assertConnectionWOUPFail("08004", dbName, "nomen", "nescio");
        // attempt to shutdown db as one of the allowed users, will fail...
        assertShutdownFail("2850H", dbName, "francois", ("francois" + PASSWORD_SUFFIX));
        // ...for only dbowner can shutdown db.
        assertShutdownWOUPOK(dbName, "APP", ("APP" + PASSWORD_SUFFIX));
        // check simple connect ok as another allowed user, also revive db
        assertConnectionOK(dbName, "jeff", ("jeff" + PASSWORD_SUFFIX));
        // but dan wasn't on the list
        assertConnectionFail("04501", dbName, "dan", ("dan" + PASSWORD_SUFFIX));
        assertShutdownFail("04501", dbName, "dan", ("dan" + PASSWORD_SUFFIX));

        // now change fullAccessUsers & test again
        conn1 = 
            openDefaultConnection("francois", ("francois" + PASSWORD_SUFFIX));
        setDatabaseProperty("derby.database.fullAccessUsers", 
            "jeff,dan,francois,jamie", conn1);
        conn1.commit();
        conn1.close();
        assertConnectionOK(dbName, "dan", ("dan" + PASSWORD_SUFFIX)); 
        assertShutdownFail("2850H", dbName, "dan", ("dan" + PASSWORD_SUFFIX));
        // but dbo was not on list...
        assertShutdownFail("04501", dbName, "APP", ("APP" + PASSWORD_SUFFIX));
        // now add dbo back in...
        conn1 = openDefaultConnection("francois", ("francois" + PASSWORD_SUFFIX));
        setDatabaseProperty(
            "derby.database.defaultConnectionMode","NoAccess", conn1);
        setDatabaseProperty(
            "derby.database.fullAccessUsers", 
            "APP,jeff,dan,francois,jamie", conn1);
        conn1.commit();
        conn1.close();

        // Invalid logins
        // bad user
        assertConnectionFail("08004", dbName, "badUser", "badPwd");
        // just checking that it's still not working if we try again
        assertConnectionFail("08004", dbName, "badUser", "badPwd");
        // system is not on the list...
        assertConnectionFail("04501", dbName, "system", "admin");
        // dan's on the list, but this isn't the pwd
        assertConnectionFail("08004", dbName, "dan", "badPwd");
        assertConnectionFail("08004", dbName, "jamie", ("dan" + PASSWORD_SUFFIX));
        // check some shutdowns
        assertShutdownFail("04501", dbName, "system", "admin");
        assertShutdownFail("08004", dbName, "badUser", "badPwd");
        assertShutdownFail("08004", dbName, "dan", "badPwd");
        assertShutdownFail("08004", dbName, "badUser", ("dan" + PASSWORD_SUFFIX));
        
        // try system shutdown with wrong user
        assertSystemShutdownFail("08004", "", "badUser", ("dan" + PASSWORD_SUFFIX));
        // with 'allowed' user but bad pwd
        assertSystemShutdownFail("08004", "", "dan", ("jeff" + PASSWORD_SUFFIX));
        // dbo, but bad pwd
        assertSystemShutdownFail("08004", "", "APP", ("POO"));
        // allowed user but not dbo
        assertSystemShutdownFail("2850H", "", "dan", ("dan" + PASSWORD_SUFFIX));
        // expect Derby system shutdown, which gives XJ015 error.
        assertSystemShutdownOK("", "APP", ("APP" + PASSWORD_SUFFIX));
        
        // so far so good. set back security properties
        conn1 = openDefaultConnection("dan", ("dan" + PASSWORD_SUFFIX));
        setDatabaseProperty(
            "derby.database.defaultConnectionMode","fullAccess", conn1);
        setDatabaseProperty(
            "derby.connection.requireAuthentication","false", conn1);
        conn1.commit();
        stmt.close();
        conn1.close();
    }

    // Experiment using USER, CURRENT_USER, etc.
    // also tests actual write activity
    public void testUserFunctions() throws SQLException
    {
        // use valid user/pwd to set the full accessusers.
        Connection conn1 = openDefaultConnection(
            "dan", ("dan" + PASSWORD_SUFFIX));
        setDatabaseProperty(
            "derby.database.fullAccessUsers", 
            "francois,jeff,ames,jerry,jamie,dan,system", conn1);
        setDatabaseProperty(
            "derby.database.defaultConnectionMode","NoAccess", conn1);
        conn1.commit();

        // we should still be connected as dan
        Statement stmt = conn1.createStatement();
        assertUpdateCount(stmt, 0, 
            "create table APP.t1(c1 varchar(30) check (UPPER(c1) <> 'JAMIE'))");
        assertUpdateCount(stmt, 1, "insert into APP.t1 values USER");
      
        conn1.commit();
        stmt.close();
        conn1.close();

        useUserValue(1, "jeff", "insert into APP.t1 values CURRENT_USER");
        useUserValue(1, "ames", "insert into APP.t1 values SESSION_USER");
        useUserValue(1, "jerry", "insert into APP.t1 values {fn user()}");
        assertUserValue(new String[] {"DAN","JEFF","AMES","JERRY"},
            "dan", "select * from APP.t1");
        // attempt some usage in where clause
        useUserValue(1,
            "dan", "update APP.t1 set c1 = 'edward' where c1 = USER");
        assertUserValue(new String[] {"JEFF"},"jeff",
            "select * from APP.t1 where c1 like CURRENT_USER");
        useUserValue(1, "ames", 
            "update APP.t1 set c1 = 'sema' where SESSION_USER = c1");
        useUserValue(1, "jerry", 
            "update APP.t1 set c1 = 'yrrej' where c1 like {fn user()}");
        assertUserValue(new String[] {"edward","JEFF","sema","yrrej"},
            "dan", "select * from APP.t1");
        useUserValue(4, "francois", "update APP.T1 set c1 = USER");
        assertUserValue(
            new String[] {"FRANCOIS","FRANCOIS","FRANCOIS","FRANCOIS"},
            "dan", "select * from APP.t1");

        // check that attempt to insert 'jamie' gives a check violation
        conn1 = openDefaultConnection("jamie", ("jamie" + PASSWORD_SUFFIX));
        stmt = conn1.createStatement();
        try {
            stmt.execute("insert into APP.t1 values CURRENT_USER");
        } catch (SQLException sqle) {
            assertSQLState("23513", sqle);
        }
        stmt.close();
        conn1.close();

        // Note: there is not much point in attempting to write with an invalid
        // user, that's already tested in the testConnectionShutdown fixture

        // reset
        conn1 = openDefaultConnection("dan", ("dan" + PASSWORD_SUFFIX));
        setDatabaseProperty(
            "derby.database.defaultConnectionMode","fullAccess", conn1);
        setDatabaseProperty(
            "derby.connection.requireAuthentication","false", conn1);
        stmt = conn1.createStatement();
        assertUpdateCount(stmt, 0, "drop table APP.t1");
        conn1.commit();
        stmt.close();
        conn1.close();
    }

    public void testChangePasswordAndDatabasePropertiesOnly() 
    throws SQLException
    {
        String dbName = TestConfiguration.getCurrent().getDefaultDatabaseName();

        // use valid user/pwd to set the full accessusers.
        Connection conn1 = openDefaultConnection(
            "dan", ("dan" + PASSWORD_SUFFIX));
        setDatabaseProperty("derby.database.fullAccessUsers", 
            "dan,jeff,system", conn1);
        setDatabaseProperty(
            "derby.database.defaultConnectionMode","NoAccess", conn1);
        setDatabaseProperty(
                "derby.database.requireAuthentication","true", conn1);
        
        conn1.commit();
        
        // check the system wide user
        assertConnectionOK(dbName, "system", "admin"); 
        assertConnectionFail("08004", dbName, "system", "otherSysPwd");
        assertConnectionOK(dbName, "jeff", ("jeff" + PASSWORD_SUFFIX));
        assertConnectionFail("08004", dbName, "jeff", "otherPwd");
        setDatabaseProperty("derby.user.jeff", "otherPwd", conn1);
        conn1.commit();
        // should have changed ok.
        assertConnectionOK(dbName, "jeff", "otherPwd");

        // note: if we do this:
        //  setDatabaseProperty("derby.user.system", "scndSysPwd", conn1);
        //  conn1.commit();
        // i.e. adding the same user (but different pwd) at database level,
        // then we cannot connect anymore using that user name, not with
        // either password.

        // force database props only
        setDatabaseProperty(
            "derby.database.propertiesOnly","true", conn1);
        conn1.commit();
        
        // now, should not be able to logon as system user
        assertConnectionFail("08004", dbName, "system", "admin");

        // reset propertiesOnly
        setDatabaseProperty(
            "derby.database.propertiesOnly","false", conn1);
        conn1.commit();
        assertConnectionOK(dbName, "system", "admin");
        
        // try changing system's pwd
        AccessController.doPrivileged
        (new java.security.PrivilegedAction(){
                public Object run(){
                    return java.lang.System.setProperty(
                        "derby.user.system", "thrdSysPwd");
                }
        });

        // can we get in as system user with changed pwd
        assertConnectionOK(dbName, "system", "thrdSysPwd");
        
        // reset
        // first change system's pwd back
        AccessController.doPrivileged
        (new java.security.PrivilegedAction(){
                public Object run(){
                    return java.lang.System.setProperty(
                        "derby.user.system", "admin");
                }
        });
        conn1 = openDefaultConnection("dan", ("dan" + PASSWORD_SUFFIX));
        setDatabaseProperty(
            "derby.database.defaultConnectionMode","fullAccess", conn1);
        setDatabaseProperty(
            "derby.connection.requireAuthentication","false", conn1);
        setDatabaseProperty(
                "derby.database.propertiesOnly","false", conn1);
        conn1.commit();
        conn1.close();
    }
    
    public void testNotFullAccessUsers() throws SQLException
    {
        // use valid user/pwd to set the full accessusers.
        Connection conn1 = openDefaultConnection(
            "dan", ("dan" + PASSWORD_SUFFIX));
        setDatabaseProperty("derby.database.fullAccessUsers", 
            "dan,jamie,system", conn1);
        // cannot set a user to both full and readonly access...
        assertFailSetDatabaseProperty(
                "derby.database.readOnlyAccessUsers", "jamie", conn1);
        setDatabaseProperty(
                "derby.database.readOnlyAccessUsers", "ames,mickey", conn1);
        setDatabaseProperty(
            "derby.database.defaultConnectionMode","NoAccess", conn1);
        setDatabaseProperty(
                "derby.database.requireAuthentication","true", conn1);
        conn1.commit();

        // we should still be connected as dan
        Statement stmt = conn1.createStatement();
        assertUpdateCount(stmt, 0, 
            "create table APP.t1(c1 varchar(30) check (UPPER(c1) <> 'JAMIE'))");
        assertUpdateCount(stmt, 1, "insert into APP.t1 values USER");
      
        conn1.commit();
        stmt.close();
        conn1.close();

        // check full access system level user can update
        conn1 = openDefaultConnection("system", "admin");
        stmt = conn1.createStatement();
        assertUpdateCount(stmt, 1, "update APP.t1 set c1 = USER");
        conn1.commit();
        stmt.close();
        conn1.close();
        
        // read only users
        assertUserValue(new String[] {"SYSTEM"},"ames", 
            "select * from APP.t1"); // should succeed
        conn1 = openDefaultConnection("ames", ("ames"+PASSWORD_SUFFIX));
        stmt = conn1.createStatement();
        assertStatementError(
            "25502", stmt, "delete from APP.t1 where c1 = 'SYSTEM'");
        assertStatementError("25502", stmt, "insert into APP.t1 values USER");
        assertStatementError(
            "25502", stmt, "update APP.t1 set c1 = USER where c1 = 'SYSTEM'");
        assertStatementError("25503", stmt, "create table APP.t2 (c1 int)");
        conn1.commit();
        stmt.close();
        // read-only system level user
        conn1 = openDefaultConnection("mickey", "mouse");
        stmt = conn1.createStatement();
        assertStatementError(
            "25502", stmt, "delete from APP.t1 where c1 = 'SYSTEM'");
        conn1.close();

        // reset
        conn1 = openDefaultConnection("dan", ("dan" + PASSWORD_SUFFIX));
        setDatabaseProperty(
            "derby.database.defaultConnectionMode","fullAccess", conn1);
        setDatabaseProperty(
            "derby.connection.requireAuthentication","false", conn1);
        stmt = conn1.createStatement();
        assertUpdateCount(stmt, 0, "drop table APP.t1");
        conn1.commit();
        stmt.close();
        conn1.close();
    }
    
    public void testGreekCharacters() throws SQLException {
        
        String dbName = TestConfiguration.getCurrent().getDefaultDatabaseName();

        // add a system level user
        AccessController.doPrivileged
        (new java.security.PrivilegedAction(){
                public Object run(){
                    String zeus = "\u0396\u0395\u03A5\u03A3";
                    String apollo = "\u0391\u09A0\u039F\u039B\u039B\u039A\u0390";
                    return java.lang.System.setProperty(
                        ("derby.user." + apollo), zeus);
                }
        });

        Connection conn1 = openDefaultConnection(
                "dan", ("dan" + PASSWORD_SUFFIX));
        // add a database level user
        setDatabaseProperty(("derby.user." + zeus), apollo, conn1);
        setDatabaseProperty("derby.database.fullAccessUsers", 
                ("dan,system,APP" + zeus + "," + apollo) , conn1);
        conn1.commit();
        conn1.close();
        
        // with network server / derbynet client, non-ascii isn't supported,
        // (or not working) see also DERBY-728, and
        // org.apache.derby.client.net.EbcdicCcsidManager
        if (usingDerbyNetClient())
        {
            assertConnectionFail("22005", dbName, zeus, apollo);
        }
        else {
            assertConnectionOK(dbName, zeus, apollo);
            assertConnectionFail("08004", dbName, apollo, apollo);
            // shutdown only allowd by DBO
            assertShutdownFail("2850H", dbName, zeus, apollo);
            assertConnectionOK(dbName, apollo, zeus);
            assertShutdownFail("08004", dbName, zeus, zeus);
            assertShutdownFail("2850H", dbName, apollo, zeus);
            assertShutdownOK(dbName, "APP", ("APP" + PASSWORD_SUFFIX));

            conn1 = openDefaultConnection(zeus, apollo);
            Statement stmt = conn1.createStatement();
            assertUpdateCount(stmt, 0, 
            "create table APP.t1(c1 varchar(30))");
            assertUpdateCount(stmt, 1, "insert into APP.t1 values USER");
            assertUserValue(new String[] {zeus}, zeus, apollo,
            "select * from APP.t1 where c1 like CURRENT_USER");
            conn1.commit();
            stmt.close();
            conn1.close();
        }
        
        // reset
        conn1 = openDefaultConnection("dan", ("dan" + PASSWORD_SUFFIX));
        setDatabaseProperty(
            "derby.database.defaultConnectionMode","fullAccess", conn1);
        setDatabaseProperty(
            "derby.connection.requireAuthentication","false", conn1);
        Statement stmt = conn1.createStatement();
        if (usingEmbedded())
            assertUpdateCount(stmt, 0, "drop table APP.t1");
        conn1.commit();
        stmt.close();
        conn1.close();
    }
    
    protected void assertFailSetDatabaseProperty(
        String propertyName, String value, Connection conn) 
    throws SQLException {
        CallableStatement setDBP =  conn.prepareCall(
        "CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(?, ?)");
        setDBP.setString(1, propertyName);
        setDBP.setString(2, value);
        // user jamie cannot be both readOnly and fullAccess
        assertStatementError("28503", setDBP);
    }
    
    protected void setDatabaseProperty(
        String propertyName, String value, Connection conn) 
    throws SQLException {
        CallableStatement setDBP =  conn.prepareCall(
        "CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(?, ?)");
        setDBP.setString(1, propertyName);
        setDBP.setString(2, value);
        setDBP.execute();
    }
    
    protected void useUserValue(int expectedUpdateCount, String user, String sql)
    throws SQLException
    {
        Connection conn1 = openDefaultConnection(user, user + PASSWORD_SUFFIX);
        Statement stmt = conn1.createStatement();
        assertUpdateCount(stmt, expectedUpdateCount, sql);
        conn1.commit();
        stmt.close();
        conn1.close();
    }
    
    protected void assertUserValue(
        String[] expected, String user, String password, String sql)
    throws SQLException
    {
        Connection conn1 = openDefaultConnection(user, password);
        Statement stmt = conn1.createStatement();
        ResultSet rs = stmt.executeQuery(sql);
        int i = 0; 
        while (rs.next())
        {
            assertEquals(expected[i],rs.getString(1));
            i++;
        }
        assertEquals(expected.length, i);
        conn1.commit();
        stmt.close();
        conn1.close();
    }
    
    // convenience method, password is often using PASSWORD_SUFFIX
    protected void assertUserValue(String[] expected, String user, String sql)
    throws SQLException {
        assertUserValue(expected, user, (user + PASSWORD_SUFFIX), sql);
    }
    
    protected void assertConnectionOK(
         String dbName, String user, String password)
    throws SQLException
    {
        DataSource ds = JDBCDataSource.getDataSource(dbName);
        try {
            assertNotNull(ds.getConnection(user, password));
        }
        catch (SQLException e) {
            throw e;
        }
    }
    
    // getConnection(), using setConnectionAttributes
    protected void assertConnectionWOUPOK(
        String dbName, String user, String password)
    throws SQLException
    {
        DataSource ds = JDBCDataSource.getDataSource(dbName);
        JDBCDataSource.setBeanProperty(ds, "user", user);
        JDBCDataSource.setBeanProperty(ds, "password", password);
        try {
            assertNotNull(ds.getConnection());
        }
        catch (SQLException e) {
            throw e;
        }
    }
    
    protected void assertConnectionFail(
        String expectedSqlState, String dbName, String user, String password)
    throws SQLException
    {
        DataSource ds = JDBCDataSource.getDataSource(dbName);
        try {
            ds.getConnection(user, password);
            fail("Connection should've been refused/failed");
        }
        catch (SQLException e) {
            assertSQLState(expectedSqlState, e);
        }
    }

    // connection without user and password
    protected void assertConnectionWOUPFail(
        String expectedError, String dbName, String user, String password) 
    throws SQLException 
    {
        DataSource ds = JDBCDataSource.getDataSource(dbName);
        JDBCDataSource.setBeanProperty(ds, "user", user);
        JDBCDataSource.setBeanProperty(ds, "password", password);
        try {
                ds.getConnection();
                fail("Connection should've been refused/failed");
        }
        catch (SQLException e) {
                assertSQLState(expectedError, e);
        }
    }

    protected void assertShutdownOK(
        String dbName, String user, String password)
    throws SQLException {

        if (usingEmbedded())
        {
            DataSource ds = JDBCDataSource.getDataSource(dbName);
            JDBCDataSource.setBeanProperty(ds, "shutdownDatabase", "shutdown");
            try {
                ds.getConnection(user, password);
                fail("expected shutdown to fail");
            } catch (SQLException e) {
                // expect 08006 on successful shutdown
                assertSQLState("08006", e);
            }
        }
        else if (usingDerbyNetClient())
        {
            ClientDataSource ds = 
                (ClientDataSource)JDBCDataSource.getDataSource(dbName);
            ds.setConnectionAttributes("shutdown=true");
            try {
                ds.getConnection(user, password);
                fail("expected shutdown to fail");
            } catch (SQLException e) {
                // expect 08006 on successful shutdown
                assertSQLState("08006", e);
            }
        }
    }

    protected void assertShutdownWOUPOK(
        String dbName, String user, String password)
    throws SQLException {

        if (usingEmbedded())
        {
            DataSource ds = JDBCDataSource.getDataSource(dbName);
            JDBCDataSource.setBeanProperty(ds, "shutdownDatabase", "shutdown");
            JDBCDataSource.setBeanProperty(ds, "user", user);
            JDBCDataSource.setBeanProperty(ds, "password", password);
            try {
                ds.getConnection();
                fail("expected shutdown to fail");
            } catch (SQLException e) {
                // expect 08006 on successful shutdown
                assertSQLState("08006", e);
            }
        }
        else if (usingDerbyNetClient())
        {
            ClientDataSource ds = 
                (ClientDataSource)JDBCDataSource.getDataSource(dbName);
            ds.setConnectionAttributes(
                    "shutdown=true;user=" + user + ";password="+password);
            try {
                ds.getConnection();
                fail("expected shutdown to fail");
            } catch (SQLException e) {
                // expect 08006 on successful shutdown
                assertSQLState("08006", e);
            }
        }
    }
    
    protected void assertShutdownFail(
        String expectedSqlState, String dbName, String user, String password) 
    throws SQLException
    {

        // with DerbyNetClient there is no Datasource setShutdownDatabase method
        if (usingEmbedded()) 
        {
            DataSource ds = JDBCDataSource.getDataSource(dbName);
            JDBCDataSource.setBeanProperty(ds, "shutdownDatabase", "shutdown");
            try {
                ds.getConnection(user, password);
                fail("expected shutdown to fail");
            } catch (SQLException e) {
                assertSQLState(expectedSqlState, e);
            }
        }
        else if (usingDerbyNetClient())
        {
            ClientDataSource ds = 
                (ClientDataSource)JDBCDataSource.getDataSource(dbName);
            ds.setConnectionAttributes("shutdown=true");
            try {
                ds.getConnection(user, password);
                fail("expected shutdown to fail");
            } catch (SQLException e) {
                assertSQLState(expectedSqlState, e);
            }
        }
    }
    
    protected void assertShutdownWOUPFail(
        String expectedSqlState, String dbName, String user, String password) 
    throws SQLException
    {
        // with DerbyNetClient there is no Datasource setShutdownDatabase 
        // method so can't use the setBeanProperty
        if (usingEmbedded()) 
        {
            DataSource ds = JDBCDataSource.getDataSource(dbName);
            JDBCDataSource.setBeanProperty(ds, "shutdownDatabase", "shutdown");
            JDBCDataSource.setBeanProperty(ds, "user", user);
            JDBCDataSource.setBeanProperty(ds, "password", password);
            try {
                ds.getConnection();
                fail("expected shutdown to fail");
            } catch (SQLException e) {
                assertSQLState(expectedSqlState, e);
            }
        }
        else if (usingDerbyNetClient())
        {
            ClientDataSource ds = 
                (ClientDataSource)JDBCDataSource.getDataSource(dbName);
            ds.setConnectionAttributes(
                "shutdown=true;user=" + user + ";password="+password);
            try {
                ds.getConnection();
                fail("expected shutdown to fail");
            } catch (SQLException e) {
                assertSQLState(expectedSqlState, e);
            }
        }
    }
    
    protected void assertSystemShutdownOK(
        String dbName, String user, String password)
    throws SQLException {
        if (usingEmbedded())
        {
            DataSource ds = JDBCDataSource.getDataSource(dbName);
            JDBCDataSource.setBeanProperty(ds, "shutdownDatabase", "shutdown");
            try {
                ds.getConnection(user, password);
                fail("expected system shutdown resulting in XJ015 error");
            } catch (SQLException e) {
                // expect XJ015, system shutdown, on successful shutdown
                assertSQLState("XJ015", e);
            }
        }
        else if (usingDerbyNetClient())
        {
            ClientDataSource ds = 
                (ClientDataSource)JDBCDataSource.getDataSource(dbName);
            ds.setConnectionAttributes("shutdown=true");
            try {
                ds.getConnection(user, password);
                fail("expected shutdown to fail");
            } catch (SQLException e) {
                // expect XJ015 on successful shutdown
                assertSQLState("XJ015", e);
            }
        }
    }

    // Note, we need a separate method for fail & OK because something
    // the framework will add the wrong details. If we use
    // getDataSource(dbName), we don't get a successful XJ015, ever,
    // if we use getDataSource(), it appears the user/password on connect
    // is ignored, at least, we get XJ015 anyway.
    // 
    protected void assertSystemShutdownFail(
        String expectedError, String dbName, String user, String password)
    throws SQLException {
        if (usingEmbedded())
        {
            DataSource ds = JDBCDataSource.getDataSource();
            JDBCDataSource.setBeanProperty(ds, "shutdownDatabase", "shutdown");
            JDBCDataSource.setBeanProperty(ds, "user", user);
            JDBCDataSource.setBeanProperty(ds, "password", password);
            try {
                ds.getConnection();
                fail("expected shutdown to fail");
            } catch (SQLException e) {
                assertSQLState(expectedError, e);
            }
        }
        else if (usingDerbyNetClient())
        {
            ClientDataSource ds = 
                (ClientDataSource)JDBCDataSource.getDataSource();
            ds.setConnectionAttributes(
                "shutdown=true;user=" + user + ";password=" + password);
            try {
                ds.getConnection();
                fail("expected shutdown to fail");
            } catch (SQLException e) {
                assertSQLState(expectedError, e);
            }
        }
    }
    
    public void assertConnectionFail(String dbName) throws SQLException {
        
        // Get the default data source but clear the user and
        // password set by the configuration.
        DataSource ds = JDBCDataSource.getDataSource(dbName);
        
        // Reset to no user/password though client requires
        // a valid name, so reset to the default
        if (usingDerbyNetClient())
            JDBCDataSource.setBeanProperty(ds, "user", "APP");
        else
            JDBCDataSource.clearStringBeanProperty(ds, "user");
        JDBCDataSource.clearStringBeanProperty(ds, "password");
        
        try {
            ds.getConnection();
            fail("expected connection to fail");
        } catch (SQLException e) {
            assertSQLState("08004", e);
        }       
    }
}
