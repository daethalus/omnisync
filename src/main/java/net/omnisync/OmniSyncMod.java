package net.omnisync;

import com.sun.net.httpserver.*;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;

@Mod(modid = OmniSyncMod.MODID, name = OmniSyncMod.NAME, version = OmniSyncMod.VERSION, serverSideOnly = true, acceptableRemoteVersions = "*")
public class OmniSyncMod
{
    public static final String MODID = "omnisync";
    public static final String NAME = "Omnisync";
    public static final String VERSION = "1.0";

    private static Logger logger;
    public static File minecraftFolder;
    public static Config config;

    public static String[] folderToCheck = new String [] {"scripts", "mods", "config", "resourcepacks", "resources"};

    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        minecraftFolder = (Launch.minecraftHome == null ? new File(".") : Launch.minecraftHome);
        logger = event.getModLog();
        try {
            config = new Config();
        } catch (Exception ex) {
            logger.error("error on load config.properties ", ex);
        }
    }

    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        MinecraftServer minecraftServer = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (minecraftServer != null) {
            try {
                FolderChecksum folderChecksum = new FolderChecksum(minecraftFolder, logger);
                folderChecksum.saveJsonFile();

                int port = 0;

                if (config != null && config.getServerURL() != null && !config.getServerURL().isEmpty()) {
                    try {
                        String[] split = config.getServerURL().split(":");
                        if (split.length > 2) {
                            port = Integer.parseInt(split[2]);
                        }
                    } catch (Exception ex) {
                        logger.error("error on load port from config.properties ", ex);
                    }
                }

                if (port > 0) {
                    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
                    HttpContext context = server.createContext("/");
                    context.setHandler(OmniSyncMod::handleRequest);
                    context.setAuthenticator(new BasicAuthenticator("/") {
                        @Override
                        public boolean checkCredentials(String user, String pwd) {
                            return user.equals(config.getUser()) && pwd.equals(config.getPass());
                        }
                    });
                    server.start();
                    logger.info("OmniSync server started");
                }
            } catch (Exception ex) {
                logger.error("error: ", ex);
            }
        }
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
        Headers h = exchange.getResponseHeaders();
        URI requestURI = exchange.getRequestURI();

        File file = new File(minecraftFolder + "/" + requestURI.toString());
        if (file.exists()) {
            byte[] buff = new byte[64*1024];
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int n = 0;
            while ((n = in.read(buff)) >= 0) {
                out.write(buff, 0, n);
            }
            in.close();
            out.close();

            byte[] bytes = out.toByteArray();

            h.add("Content-Disposition", "attachment; filename=" + file.getName());
            h.add("Content-Type", "application/x-zip-compressed");

            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        } else {
            logger.info("OmniSync: requested file: {} not exists ", requestURI.toString());

            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            os.close();
        }
    }
}
