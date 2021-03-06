package Tux2.TuxTwoLib;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/**
 * TuxTwoLib for Bukkit
 *
 * @author Tux2
 */
public class TuxTwoLib extends JavaPlugin {

    String ttlbuild = "7";
    public boolean hasupdate = false;
    public String newversion = "";
    public boolean updatefailed = false;
    boolean checkforupdates = true;
    boolean autodownloadupdates = true;
    boolean autodownloadupdateonnewmcversion = true;
    public boolean updatesuccessful = false;

    String currentMCversion = "1.10";
    String currentNMS = "v1_10_R1";

    String versionName = null;
    private String versionLink = null;
    String mcversion = this.currentMCversion;

    private static final String TITLE_VALUE = "name"; // Gets remote file's title
    private static final String LINK_VALUE = "downloadUrl"; // Gets remote file's download link

    boolean incompatiblemcversion = false;

    WarningsThread warnings = null;

    public TuxTwoLib() {
        super();
    }



    @Override
    public void onEnable() {

        final FileConfiguration config = this.getConfig();
        final File configfile = new File(this.getDataFolder().toString() + "/config.yml");
        if (!configfile.exists()) {
            config.set("AutoDownloadUpdates", true);
            config.set("CheckForUpdates", true);
            config.set("AutoUpdateOnMinecraftVersionChange", true);
            this.saveConfig();
        }
        this.autodownloadupdates = config.getBoolean("AutoDownloadUpdates", true);
        this.autodownloadupdateonnewmcversion = config.getBoolean("AutoUpdateOnMinecraftVersionChange", true);
        this.checkforupdates = config.getBoolean("CheckForUpdates", true);

        final Pattern bukkitversion = Pattern.compile("(\\d+\\.\\d+\\.?\\d*)-R(\\d\\.\\d)");
        final String ver = this.getServer().getBukkitVersion();
        final Matcher bukkitmatch = bukkitversion.matcher(ver);
        // ----------------1.5 code--------------------
        if (bukkitmatch.find()) {
            this.mcversion = bukkitmatch.group(1);
            if (!this.mcversion.equals(this.currentMCversion) && !this.currentNMS.equals(this.getNMSVersion())) {
                if (this.autodownloadupdateonnewmcversion) {
                    this.getLogger().warning("Current version incompatible with this version of Craftbukkit! Checking for and downloading a compatible version.");
                    final boolean result = this.updatePlugin(this.mcversion, false);
                    if (result && !this.updatefailed) {
                        this.getLogger().warning("New version downloaded successfully. Make sure to restart your server to restore full functionality!");
                    } else {
                        this.incompatiblemcversion = true;
                        this.getLogger().severe("New version download was unsuccessful. Please download the correct version of the library from http://dev.bukkit.org/server-mods/tuxtwolib/");
                    }
                } else {
                    if (this.checkforupdates) {
                        final String versioncheck = this.updateAvailable(this.mcversion, true);
                        if (!versioncheck.equals("0")) {
                            this.newversion = versioncheck;
                            this.getLogger().severe("Craftbukkit revision is incompatible with this build! Please download " + this.newversion + " version of the library from http://dev.bukkit.org/server-mods/tuxtwolib/");
                        } else {
                            this.getLogger().severe("Craftbukkit revision is incompatible with this build! Please download the correct version of the library from http://dev.bukkit.org/server-mods/tuxtwolib/");
                        }
                    } else {
                        this.getLogger().severe("Craftbukkit revision is incompatible with this build! Please download the correct version of the library from http://dev.bukkit.org/server-mods/tuxtwolib/");
                    }
                }
                this.incompatiblemcversion = true;
                // register events for server admins.
                this.getServer().getPluginManager().registerEvents(new TuxTwoLibWarningsListener(this), this);
            } else {
                this.getServer().getPluginManager().registerEvents(new TuxTwoListener(), this);
                // This version of minecraft is compatible. Let's do the optional update check.
                if (this.checkforupdates) {
                    final String versioncheck = this.updateAvailable(this.mcversion, true);
                    if (!versioncheck.equals("0")) {
                        // We have an update! Set the newversion string to the name of the new
                        // version.
                        this.newversion = versioncheck;
                        // We can update the plugin in the background.
                        if (this.autodownloadupdates) {
                            this.getLogger().warning("Update available! Downloading in the background.");
                            if (!this.updatePlugin(this.mcversion, true)) {
                                this.getLogger().info("Update failed! Please download " + this.newversion + " version of the library from http://dev.bukkit.org/server-mods/tuxtwolib/ manually.");
                            }
                        } else {
                            this.getLogger().info("A new version for your version of Craftbukkit is available! Please download " + this.newversion + " version of the library from http://dev.bukkit.org/server-mods/tuxtwolib/");
                        }
                    }
                    // register events for server admins.
                    this.getServer().getPluginManager().registerEvents(new TuxTwoLibWarningsListener(this), this);
                }
            }
        } else {
            this.getLogger().warning("Unable to verify minecraft version! MC version reported: " + ver);
        }
    }

    @Override
    public void onDisable() {

    }

    public String updateAvailable(final String version, final boolean returnversion) {
        final boolean result = this.read();
        if (result) {
            if (returnversion) {
                return this.versionName;
            } else {
                return "1";
            }
        } else {
            return "0";
        }
    }

    public boolean updatePlugin(final String version, final boolean threaded) {
        if (this.updateAvailable(version, false).equals("0")) {
            return false;
        }
        final DownloadPluginThread dpt = new DownloadPluginThread(this.getDataFolder().getParent(), this.versionLink, new File(this.getServer().getUpdateFolder() + File.separator + this.getFile()), this);
        if (threaded) {
            final Thread downloaderthread = new Thread(dpt);
            downloaderthread.start();
        } else {
            dpt.run();
        }
        return true;
    }

    private boolean read() {
        String currentminecraftversion = this.mcversion;
        if (this.currentNMS.equals(this.getNMSVersion())) {
            currentminecraftversion = this.currentMCversion;
        }
        try {
            final URL url = new URL("https://api.curseforge.com/servermods/files?projectIds=48210");
            final URLConnection conn = url.openConnection();
            conn.setConnectTimeout(5000);

            conn.addRequestProperty("User-Agent", "Updater (by Gravity)");

            conn.setDoOutput(true);

            final BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            final String response = reader.readLine();

            final JSONArray array = (JSONArray) JSONValue.parse(response);

            if (array.size() == 0) {
                this.getLogger().warning("The updater could not find any files for the TuxTwoLib project!");
                return false;
            }
            boolean foundupdate = false;
            for (int i = array.size() - 1; i < -1 && !foundupdate; i--) {
                this.versionName = (String) ((JSONObject) array.get(i)).get(TuxTwoLib.TITLE_VALUE);
                this.versionLink = (String) ((JSONObject) array.get(i)).get(TuxTwoLib.LINK_VALUE);
                final String[] versionsplit = this.versionName.split("-");
                if (versionsplit.length > 1) {
                    // Let's see if it is for the correct mc version.
                    if (versionsplit[0].equalsIgnoreCase("v" + currentminecraftversion)) {
                        // If the current MC version is the same as the version this
                        // plugin was built for, then we need to check build numbers
                        if (currentminecraftversion.equalsIgnoreCase(this.currentMCversion)) {
                            final String buildnumber = versionsplit[1].substring(1);
                            try {
                                final int build = Integer.parseInt(buildnumber);
                                final int currentbuild = Integer.parseInt(this.ttlbuild);
                                // Since the files go backwards, then if it's bigger
                                // than the current build it must be the newest
                                if (currentbuild < build) {
                                    foundupdate = true;
                                }
                            } catch (final NumberFormatException e) {

                            }
                        }
                    }
                }
            }
            return foundupdate;
        } catch (final IOException e) {
            if (e.getMessage().contains("HTTP response code: 403")) {
                this.getLogger().warning("dev.bukkit.org rejected the API key provided in plugins/Updater/config.yml");
                this.getLogger().warning("Please double-check your configuration to ensure it is correct.");
            } else {
                this.getLogger().warning("The updater could not contact dev.bukkit.org for updating.");
                this.getLogger().warning("If you have not recently modified your configuration and this is the first time you are seeing this message, the site may be experiencing temporary downtime.");
            }
            e.printStackTrace();
            return false;
        }
    }

    private String getNMSVersion() {
        final String name = this.getServer().getClass().getPackage().getName();
        final String mcVersion = name.substring(name.lastIndexOf('.') + 1);
        return mcVersion;
    }
}

