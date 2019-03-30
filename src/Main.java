import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import soot.options.Options;
import utils.ExtendedDefaultParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;


public class Main {

    private static String package_name;
    private static String apktool_dir;
    private static String token_files_dir;

    private static void commandParser(ExtendedDefaultParser parser, String[] args) {
        // 命令行解析器
        org.apache.commons.cli.Options options = new org.apache.commons.cli.Options();
        options.addOption("package", true, "apk package name");
        options.addOption("atd", "apktool-dir", true, "directory of apktool result");
        options.addOption("td", "token-dir", true, "directory to save token list file");

        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args, false);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            new HelpFormatter().printHelp("utility-name", options);
            System.exit(1);
        }

        package_name = cmd.getOptionValue("package");
        apktool_dir = cmd.getOptionValue("atd");
        token_files_dir = cmd.getOptionValue("td");

        System.out.println("Package name: " + package_name);
        System.out.println("Apktool directory path: " + apktool_dir);
    }

    private static void writeTokensFile(String layoutDirPath, String tokenPath) {
        File folder = new File(layoutDirPath);
        File[] files = folder.listFiles();
        if (files != null) {
            System.out.println(files.length + " layout files read from APK processing ...");
            try {
                Path tokenFilePath = Paths.get(tokenPath);
                Files.deleteIfExists(tokenFilePath);
                Files.createFile(tokenFilePath);

                StringBuilder itemsb = new StringBuilder();
                StringBuilder layoutsb = new StringBuilder();
                for (File f : files) {
                    if (f.isFile()) {
                        // 打开每个文件进行解析，解析结果为 tokens
                        String fileName = f.getName();
                        String fileNameLower = fileName.toLowerCase();
                        Dom4jParser parser = new Dom4jParser(f.getAbsolutePath());
                        parser.parse();
                        List<String> tokens = parser.getTokens();

                        if (tokens.size() > 5) {
                            boolean isLayout = fileNameLower.contains("fragment") || fileNameLower.contains("activity");
                            boolean isListItem = fileNameLower.startsWith("item_") || fileNameLower.endsWith("_item.xml") || fileNameLower.contains("_item_") ||
                                    fileNameLower.startsWith("row_") || fileNameLower.endsWith("_row.xml") || fileNameLower.contains("_row_") ||
                                    fileNameLower.startsWith("card_") || fileNameLower.endsWith("_card.xml") || fileNameLower.contains("_card_") ||
                                    fileNameLower.startsWith("cardview_") || fileNameLower.endsWith("_cardview.xml") || fileNameLower.contains("_cardview_") ||
                                    fileNameLower.startsWith("listitem_") || fileNameLower.endsWith("_listitem.xml") || fileNameLower.contains("_listitem_");
                            boolean shouldExclude = fileNameLower.startsWith("abc_") || fileNameLower.startsWith("preference_") || fileNameLower.startsWith("notification_") ||
                                    fileNameLower.startsWith("date_picker_") || fileNameLower.startsWith("time_picker_") || fileNameLower.startsWith("select_dialog_") ||
                                    fileNameLower.startsWith("support_simple_spinner_dropdown_item");

                            if (!shouldExclude) {
                                if (isListItem) {
                                    itemsb.append(package_name).append(" 2 ").append(fileName).append(" ").append(String.join(" ", tokens)).append("\n");
                                } else if (isLayout) {
                                    layoutsb.append(package_name).append(" 1 ").append(fileName).append(" ").append(String.join(" ", tokens)).append("\n");
                                }
                            }
                        }
                    }
                }

                Files.write(tokenFilePath, itemsb.toString().getBytes(), StandardOpenOption.APPEND);
                Files.write(tokenFilePath, layoutsb.toString().getBytes(), StandardOpenOption.APPEND);

                System.out.println("Output saved in " + tokenPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {

        ExtendedDefaultParser cliParser = new ExtendedDefaultParser();
        commandParser(cliParser, args);

        Options.v().set_src_prec(Options.src_prec_apk); // -src-prec apk
        Options.v().set_output_format(Options.output_format_jimple); //-f J

//        LayoutRetriever vlr = new LayoutRetriever(package_name);
//        PackManager.v().getPack("jtp").add(
//                new Transform("jtp.myInstrumenter", vlr));

        // Soot starts here
        soot.Main.main(cliParser.getNotParsedArgs());

        System.out.println("process_dir: " + Options.v().process_dir());
//        System.out.println("soot classes: " + Scene.v().getClasses());
//        System.out.println("------------\nvalid layout: " + vlr.getValidLayoutFileName());

        long startTime = System.currentTimeMillis();
        writeTokensFile(apktool_dir + File.separator + "res" + File.separator + "layout",
                token_files_dir + File.separator + package_name + "-layout.lst");
        long endTime = System.currentTimeMillis();
        System.out.println("Writing files time：" + (endTime - startTime) + "ms");

    }

}
