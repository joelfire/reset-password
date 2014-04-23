package com.datasynapse.gridserver.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.hsqldb.DatabaseManager;

import com.datasynapse.commons.security.MessageDigestGenerator;
import com.datasynapse.commons.util.ConversionUtils;
import com.datasynapse.commons.util.StringUtils;
import com.livecluster.admin.beans.UserInfo;
import com.livecluster.ext.HsqldbAdminPlugin;

public class RootPasswordUpdater {
    private static PrintStream out = System.out;
    public static void main(String[] args)  {
        usage();
        RootPasswordUpdater up = new RootPasswordUpdater();
        String path = null;
        switch (args.length) {
        case 0:
            try {
                path = up.findPath();
            } catch (RPUException e) {
                out.println("Cannot continue: " + e.getMessage());
                System.exit(1);
            }
            break;
        case 1:
            path = args[0];
            break;
        default:
            out.println("Incorrect number of arguments");
            System.exit(1);
        }
        
        out.println("The path to your internal database: " + path );
        out.println("If this is not correct, exit now." );
        out.println();
        out.println("IMPORTANT: You must shut down the Director before proceeding!");
        out.println("Press any key to continue");
        
        try {
            up.readInput();
            up.run(path);
            out.println("Password sucessfully updated.");
        } catch (IOException e) {
            System.out.println("Could not update: " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("Could not update: " + e.getMessage());
        } catch (RPUException e) {
            System.out.println("Could not update: " + e.getMessage());
        }
        
    }
    
    private static void usage() {
        out.println("This will update the root user's password in the internal database.");
        out.println();
        out.println("The DS_DATA_DIR (versions 6.x) or DS_BASEDIR (versions 5.x)");
        out.println("is used to find the internal DB directory.");
        out.println("If not set, it is assumed you are running this script out of the");
        out.println("installation directory of a 5.x Manager with no base directory");
        out.println("You may also specify the full path of the internal database directory as an");
        out.println("argument on the command-line. For example, '/opt/datasynapse/manager-data/db'");
        out.println();
    }

    private void run(String path) throws IOException, SQLException, RPUException {
        String username = getUsername();
        String password = getPassword();
        updateDB(path, username, password);
    }
    
    private String findPath() throws RPUException {
        String dataDir = System.getenv("DS_DATA_DIR");
        if (dataDir != null) {
            File f = new File(dataDir, "db" );
            if (!f.exists()) {
                throw new RPUException("The DS_DATA_DIR environment variable is set to " + dataDir +", but the db directory was not found: " + f);
            }
            out.println("Found the database path based on the DS_DATA_DIR environment variable");
            return f.getPath();
        }
        dataDir = System.getenv("DS_BASEDIR");
        if (dataDir != null) {
            File f = new File(dataDir, "WEB-INF/db/internal" );
            if (!f.exists()) {
                throw new RPUException("The DS_BASEDIR environment variable is set to " + dataDir +", but the db directory was not found: " + f);
            }
            out.println("Found the database path based on the DS_BASEDIR environment variable");
            return f.getPath();
        }
        File f = new File("webapps/livecluster/WEB-INF/db/internal" );
        if (f.exists()) {
            out.println("Found the database path based on running this script from the installation directory");
            return f.getPath();
        }
        throw new RPUException("No database directory was found.");
    }
    
    private String getUsername() throws IOException {
        String msg = "What is the name of the root user? ";
        if (System.console() != null) {
            return System.console().readLine(msg, (Object[])null);
        }
        System.out.print(msg);
        return readInput();
    }
    
    private String getPassword() throws IOException, RPUException {
        String pw1 = readPassword("Please enter the new password: ");
        String pw2 = readPassword("Please enter the new password again: ");
        if (!pw1.equals(pw2)) {
            throw new RPUException("Passwords do not match");
        }
        byte[] ba = pw1.getBytes();
        MessageDigestGenerator mdg = new MessageDigestGenerator("SHA-512");
        ba = mdg.generate(ba);
        return ConversionUtils.encodeBase64(ba);
    }
    
    private String readPassword(String msg) throws IOException {
        if (System.console() != null) {
            char[] c = System.console().readPassword(msg, (Object[]) null);
            return new String(c).trim();
        }
        System.out.print(msg);
        return readInput();
    }
    
    private String readInput() throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        return br.readLine();
    }
    
    private void updateDB(String path, String username, String password) throws IOException, RPUException, SQLException {
        HsqldbAdminPlugin db = initDatabase(path, "org.hsqldb.jdbcDriver", "jdbc:hsqldb:file:" + new File(path, "Internal").getPath());
        try {
            
            Connection cn;
            try {
                cn = DriverManager.getConnection(db.getUrl(), "admin", "admin");
            } catch (SQLException e) {
                throw new RPUException("There is a problem with your database, please check your path and try again: " + e);
            }
            Statement st = cn.createStatement();
            String sqlString = "SELECT * FROM USERS WHERE USERNAME = '" + username + "'";
            if (!st.execute(sqlString)) {
                throw new RPUException("User not found: " + username); 
            }
            ResultSet resultSet = st.getResultSet();
            boolean next = resultSet.next();
            if (!next) {
                throw new RPUException("User not found: " + username); 
            }
            String info = resultSet.getString("USER_INFO");
            st.close();
            UserInfo userInfo = new UserInfo(info);
            userInfo.setPassword(password);
            sqlString = "UPDATE USERS SET USER_INFO = '" + userInfo.toString() +"' WHERE USERNAME = '" + username + "'";
            st = cn.createStatement();
            st.execute(sqlString);
            st.close();
            cn.close();
        } finally {
            DatabaseManager.closeDatabases(0);
        }
    }
    
    private HsqldbAdminPlugin initDatabase(String dbdir, String driver, String url) throws IOException, RPUException  {
        HsqldbAdminPlugin plugin = new HsqldbAdminPlugin();
        plugin.setDatabaseDir(dbdir);
        plugin.setJdbcServerPort(5001);
        plugin.setName(new File(dbdir).getName());
        plugin.setDriver(driver);
        plugin.setUrl(StringUtils.replace(url, "%port%", String.valueOf(plugin.getJdbcServerPort())));
        try {
            Class.forName(plugin.getDriver());
        } catch (ClassNotFoundException e) {
            throw new RPUException("The hsqldb jar is not in your classpath");
        }
        return plugin;
    }
    
    private class RPUException extends Exception{
        private static final long serialVersionUID = 1L;

        public RPUException(String message) {
            super(message);
        }
    }
    

}
