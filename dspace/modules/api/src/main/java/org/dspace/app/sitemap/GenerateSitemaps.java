/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.sitemap;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.Logger;
import org.dspace.content.*;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.app.sitemap.*;
import org.dspace.workflow.DryadWorkflowUtils;

/**
 * Command-line utility for generating HTML and Sitemaps.org protocol Sitemaps.
 *
 * @author Robert Tansley
 * @author Stuart Lewis
 */
public class GenerateSitemaps
{
    /** Logger */
    private static Logger log = Logger.getLogger(GenerateSitemaps.class);
    private static String myDataPkgColl = ConfigurationManager.getProperty("stats.datapkgs.coll");

    public static void main(String[] args) throws Exception
    {
        final String usage = GenerateSitemaps.class.getCanonicalName();

        CommandLineParser parser = new PosixParser();
        HelpFormatter hf = new HelpFormatter();

        Options options = new Options();

        options.addOption("h", "help", false, "help");
        options.addOption("s", "no_sitemaps", false,
                "do not generate sitemaps.org protocol sitemap");
        options.addOption("b", "no_htmlmap", false,
                "do not generate a basic HTML sitemap");
        options.addOption("a", "ping_all", false,
                "ping configured search engines");
        options
                .addOption("p", "ping", true,
                        "ping specified search engine URL");

        CommandLine line = null;

        try
        {
            line = parser.parse(options, args);
        }
        catch (ParseException pe)
        {
            hf.printHelp(usage, options);
            System.exit(1);
        }

        if (line.hasOption('h'))
        {
            hf.printHelp(usage, options);
            System.exit(0);
        }

        if (line.getArgs().length != 0)
        {
            hf.printHelp(usage, options);
            System.exit(1);
        }

        /*
         * Sanity check -- if no sitemap generation or pinging to do, print
         * usage
         */
        if (line.getArgs().length != 0 || line.hasOption('b')
                && line.hasOption('s') && !line.hasOption('g')
                && !line.hasOption('m') && !line.hasOption('y')
                && !line.hasOption('p'))
        {
            System.err
                    .println("Nothing to do (no sitemap to generate, no search engines to ping)");
            hf.printHelp(usage, options);
            System.exit(1);
        }

        // Note the negation (CLI options indicate NOT to generate a sitemap)
        if (!line.hasOption('b') || !line.hasOption('s'))
        {
            try{
            generateSitemaps(!line.hasOption('b'), !line.hasOption('s'));
            }catch(Exception e){
                              System.out.print("\ngenerate site map:"+e.getMessage()+"\n");
            }
        }

        if (line.hasOption('a'))
        {
            pingConfiguredSearchEngines();
        }

        if (line.hasOption('p'))
        {
            try
            {
                pingSearchEngine(line.getOptionValue('p'));
            }
            catch (MalformedURLException me)
            {
                System.err
                        .println("Bad search engine URL (include all except sitemap URL)");
                System.exit(1);
            }
        }

        System.exit(0);
    }

    /**
     * Generate sitemap.org protocol and/or basic HTML sitemaps.
     *
     * @param makeHTMLMap
     *            if {@code true}, generate an HTML sitemap.
     * @param makeSitemapOrg
     *            if {@code true}, generate an sitemap.org sitemap.
     * @throws SQLException
     *             if a database error occurs.
     * @throws IOException
     *             if IO error occurs.
     */
    public static void generateSitemaps(boolean makeHTMLMap,
                                        boolean makeSitemapOrg) throws SQLException, IOException
    {


        try{
        String resourceURL =  ConfigurationManager.getProperty("dspace.url")+"/resource/";

        String sitemapStem = ConfigurationManager.getProperty("dspace.url")
                + "/sitemap";
        String htmlMapStem = ConfigurationManager.getProperty("dspace.url")
                + "/htmlmap";
        String handleURLStem = ConfigurationManager.getProperty("dspace.url")
                + "/handle/";

        File outputDir = new File(ConfigurationManager.getProperty("sitemap.dir"));
        if (!outputDir.exists() && !outputDir.mkdir())
        {
            log.error("Unable to create output directory");
        }
        AbstractGenerator html = null;
        AbstractGenerator sitemapsOrg = null;
        Date lastGererateDate = null;
        if (makeHTMLMap)
        {
            html = new HTMLSitemapGenerator(outputDir, htmlMapStem + "?map=",
                    null);

            lastGererateDate=getLastTimeStamp(html,outputDir);
        }

        if (makeSitemapOrg)
        {
            sitemapsOrg = new SitemapsOrgGenerator(outputDir, sitemapStem
                    + "?map=", null);

            lastGererateDate= getLastTimeStamp(sitemapsOrg,outputDir);
        }
        Context c = new Context();

        boolean fileOpen=false;
        boolean fileOpenSiteMap=false;

        ItemIterator allItems = null;
        if(lastGererateDate==null)
            allItems=Item.findAll(c);
        else
            allItems = Item.findByLastModifiedGreaterThan(c,lastGererateDate);
        try
        {
            int itemCount = 0;

            List<Item> modifiedDP = new ArrayList<Item>();
            Item i = null;
            while (allItems.hasNext())
            {
                try {
                    i = allItems.next();
                    if (!i.isWithdrawn()) {
                        Item dataPackage = i;

                        if (!i.getOwningCollection().getHandle().equals(myDataPkgColl)) {
                            dataPackage = DryadWorkflowUtils.getDataPackage(c, i);
                        }
                        String url = "";
                        if (dataPackage != null && !dataPackage.isWithdrawn()) {
                            DCValue[] identifier = dataPackage.getMetadata("dc.identifier");
                            if (identifier != null && identifier.length > 0) {

                                url = resourceURL + identifier[0].value;
                            } else if (dataPackage.getHandle() != null) {
                                url = handleURLStem + dataPackage.getHandle();
                            } else {
                                url = handleURLStem + "item/" + dataPackage.getID();
                            }
                            if (!modifiedDP.contains(dataPackage)) {
                                if (makeHTMLMap) {
                                    html.addURL(url, null);
                                    fileOpen = true;
                                }
                                if (makeSitemapOrg) {
                                    sitemapsOrg.addURL(url, null);
                                    fileOpenSiteMap = true;
                                }
                                modifiedDP.add(dataPackage);
                            }
                        } else {
                            if (dataPackage.isWithdrawn()) {
                                System.out.println("Item : " + i.getID() + " - " + i.getHandle() + ": withdrawn.");
                            } else {
                                System.out.println("Item : " + i.getID() + " - " + i.getHandle() + ": can't find the datapackage information.");
                            }
                        }
                    } else {
                        System.out.println("Item : " + i.getID() + " - " + i.getHandle() + ": can't find the datapackage information.");
                    }
                } catch (Exception ex) {
                    // if some items are not consistent just go ahead...
                    System.out.println("Item : " + i.getID() + " - " + i.getHandle() + ": not processed.");
                    log.info("Item : " + i.getID() + " - " + i.getHandle() + ": not processed.");
                }
            }

            if (makeHTMLMap)
            {
                if(fileOpen){
                    int files = html.finish();
                    log.info(LogManager.getHeader(c, "write_sitemap",
                            "type=html,num_files=" + files));
                }
                else{
                    System.out.println("Nothing to do. Since last creation no items were created or updated.");
                }
            }

            if (makeSitemapOrg)
            {
                if(fileOpenSiteMap){
                    int files = sitemapsOrg.finish();
                    log.info(LogManager.getHeader(c, "write_sitemap",
                            "type=html,num_files=" + files));
                }else{
                    System.out.println("Anything to do. SInce last creation no items were created or updated.");
                }
            }
        }
        finally
        {
            if (allItems != null)
            {
                allItems.close();
            }
        }
        System.out.println("Process terminated. Sitemaps have been generated.");
        c.abort();
        }catch (Exception ex){
            ex.printStackTrace(System.out);
        }
    }

    /**
     * Ping all search engines configured in {@code dspace.cfg}.
     *
     * @throws UnsupportedEncodingException
     *             theoretically should never happen
     */
    public static void pingConfiguredSearchEngines()
            throws UnsupportedEncodingException
    {
        String engineURLProp = ConfigurationManager
                .getProperty("sitemap.engineurls");
        String engineURLs[] = null;

        if (engineURLProp != null)
        {
            engineURLs = engineURLProp.trim().split("\\s*,\\s*");
        }

        if (engineURLProp == null || engineURLs == null
                || engineURLs.length == 0 || engineURLs[0].trim().equals(""))
        {
            log.warn("No search engine URLs configured to ping");
            return;
        }

        for (int i = 0; i < engineURLs.length; i++)
        {
            try
            {
                pingSearchEngine(engineURLs[i]);
            }
            catch (MalformedURLException me)
            {
                log.warn("Bad search engine URL in configuration: "
                        + engineURLs[i]);
            }
        }
    }

    /**
     * Ping the given search engine.
     *
     * @param engineURL
     *            Search engine URL minus protocol etc, e.g.
     *            {@code www.google.com}
     * @throws MalformedURLException
     *             if the passed in URL is malformed
     * @throws UnsupportedEncodingException
     *             theoretically should never happen
     */
    public static void pingSearchEngine(String engineURL)
            throws MalformedURLException, UnsupportedEncodingException
    {
        // Set up HTTP proxy
        if ((ConfigurationManager.getProperty("http.proxy.host") != null)
                && (ConfigurationManager.getProperty("http.proxy.port") != null))
        {
            System.setProperty("proxySet", "true");
            System.setProperty("proxyHost", ConfigurationManager
                    .getProperty("http.proxy.host"));
            System.getProperty("proxyPort", ConfigurationManager
                    .getProperty("http.proxy.port"));
        }

        String sitemapURL = ConfigurationManager.getProperty("dspace.url")
                + "/sitemap";

        URL url = new URL(engineURL + URLEncoder.encode(sitemapURL, "UTF-8"));

        try
        {
            HttpURLConnection connection = (HttpURLConnection) url
                    .openConnection();

            BufferedReader in = new BufferedReader(new InputStreamReader(
                    connection.getInputStream()));

            String inputLine;
            StringBuffer resp = new StringBuffer();
            while ((inputLine = in.readLine()) != null)
            {
                resp.append(inputLine).append("\n");
            }
            in.close();

            if (connection.getResponseCode() == 200)
            {
                log.info("Pinged " + url.toString() + " successfully");
            }
            else
            {
                log.warn("Error response pinging " + url.toString() + ":\n"
                        + resp);
            }
        }
        catch (IOException e)
        {
            log.warn("Error pinging " + url.toString(), e);
        }
    }


    public static void setFileCount(AbstractGenerator handle,int number){
        handle.fileCount =number;
    }

    public static Date getLastTimeStamp(AbstractGenerator handle,File outputDir){
        int number = 0;
        File[] files= outputDir.listFiles();
        String key = ".html";
        Date lastGenerateDate = null;
        // DCDate now = DCDate.getCurrent();
        if(handle instanceof HTMLSitemapGenerator)
        {
            key = ".html";
        }
        if(handle instanceof SitemapsOrgGenerator)
        {
            key = ".xml.gz";
        }
        if(files!=null&&files.length>0){
            for(File file : files){

                if(file.getName().contains(key)&&file.getName().startsWith("sitemap")&&!file.getName().contains("_index"))
                {
                    int startPos =   "sitemap".length();
                    int endPos =file.getName().indexOf(key);
                    String name = file.getName();
                    String index = name.substring(startPos,endPos);
                    int newNumber=0;
                    try{
                    newNumber =Integer.parseInt(index);
                    }
                    catch (Exception e)
                    {
                        log.warn("Found some files may contain sitemap infomations:" + file.getName(), e);
                        System.out.println("Found some files may contain sitemap infomations:" + file.getName()+". If this is the first time to generate the sitemap, please clean the old files in the sitemap folder");
                    }
                    if(newNumber>number)
                    {
                        number=newNumber;
                        lastGenerateDate = new Date(file.lastModified());

                    }
                }
            }

        }
        setFileCount(handle,number+1);
        return lastGenerateDate;
    }

    public static boolean checkModification(Context context,Item i, Date lastGenerateDate){
        Boolean modified =false;
        Date lastMod = i.getLastModified();
        DCDate now = DCDate.getCurrent();

        DateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        try{
            if(i.isArchived()){

                if(lastGenerateDate==null||lastMod.after(lastGenerateDate))
                {   //item hase been modified
                    modified=true;
                }
                else
                {
                    ////check the items files belong to this package item (embargo and modified)

                    Item[] dataFiles = DryadWorkflowUtils.getDataFiles(context, i);
                    if(dataFiles.length>0)
                    {
                        System.out.print(dataFiles.length+"here\n");
                    }
                    for(Item dataFile:dataFiles)
                    {
                        if(dataFile.isArchived()){
                            //check modified
                            Date lastModified = dataFile.getLastModified();
                            if(lastModified.after(lastGenerateDate)){
                                 modified=true;
                            }
                            //check embargo
                            DCValue[] embargos  = dataFile.getMetadata("dc.date.embargoedUntil");
                            for(DCValue embargo:embargos){
                                 Date embargoDate = parser.parse(embargo.toString());
                                 if(embargoDate.after(lastGenerateDate)&&embargoDate.before(now.toDate()))
                                 {
                                     modified=true;
                                 }
                            }


                        }
                    }



                }
            }
        }catch (Exception e){
            log.info("error when checking whether an item is a data package or not!");
        }
        return modified;
    }
}
