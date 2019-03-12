import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import soot.PackManager;
import soot.Transform;
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

    public static void main(String[] args) {

        // 命令行解析器
        org.apache.commons.cli.Options options = new org.apache.commons.cli.Options();
        options.addOption("package", true, "apk package name");
        options.addOption("atd", "apktool-dir", true, "directory of apktool result");
        options.addOption("td", "token-dir", true, "directory to save token list file");

        ExtendedDefaultParser cliParser = new ExtendedDefaultParser();
        CommandLine cmd = null;

        try {
            cmd = cliParser.parse(options, args, false);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            new HelpFormatter().printHelp("utility-name", options);
            System.exit(1);
        }

        String package_name = cmd.getOptionValue("package");
        String apktool_dir = cmd.getOptionValue("atd");
        String token_files_dir = cmd.getOptionValue("td");

        Options.v().set_src_prec(Options.src_prec_apk); // -src-prec apk
        Options.v().set_output_format(Options.output_format_jimple); //-f J

        LayoutRetriever vlr = new LayoutRetriever(package_name);
        PackManager.v().getPack("jtp").add(
                new Transform("jtp.myInstrumenter", vlr));

        soot.Main.main(cliParser.getNotParsedArgs());

        System.out.println("process_dir: " + Options.v().process_dir());
//        System.out.println("soot classes: " + Scene.v().getClasses());
        System.out.println("------------\nvalid layout: " + vlr.getValidLayoutFileName());

        // 开始解析 res/layout 下的每个文件
        File folder = new File(apktool_dir + "\\res\\layout");
        File[] files = folder.listFiles();
        if (files != null) {
            System.out.println(files.length + " layout files read from APK start processing ...");
            try {
                Path path = Paths.get(token_files_dir + "\\" + package_name + "-layout.lst");
                Files.deleteIfExists(path);
                Files.createFile(path);
                for (File f : files) {
                    if (f.isFile()) {
                        Dom4jParser parser = new Dom4jParser(f.getAbsolutePath());
                        parser.parse();

                        List<String> tokens = parser.getTokens();
                        if (tokens.size() > 5) {
                            System.out.println("[TOKENS] " + f.getName() + " " + String.join(" ", tokens));
                            boolean isLayout = f.getName().contains("fragment") || f.getName().contains("activity");
                            boolean isListItem = f.getName().contains("item") || f.getName().contains("row");
                            if (isListItem) {
                                Files.write(path, (package_name + " 2 " + f.getName() + " " + String.join(" ", tokens) + "\n").getBytes(), StandardOpenOption.APPEND);
                            } else if (isLayout) {
                                Files.write(path, (package_name + " 1 " + f.getName() + " " + String.join(" ", tokens) + "\n").getBytes(), StandardOpenOption.APPEND);
                            }

                        }
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }

}
