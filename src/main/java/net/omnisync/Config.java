package net.omnisync;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {

    private String serverURL;
    private String user;
    private String pass;

    public Config() throws IOException {
        InputStream input = OmniLauncher.class.getResourceAsStream("/config.properties");

        Properties prop = new Properties();
        prop.load(input);

        serverURL = prop.getProperty("server.url");
        user = prop.getProperty("http.user");
        pass = prop.getProperty("http.pass");
    }

    public String getServerURL() {
        return serverURL;
    }

    public String getUser() {
        return user;
    }

    public String getPass() {
        return pass;
    }
}
