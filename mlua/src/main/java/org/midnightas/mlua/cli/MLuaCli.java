package org.midnightas.mlua.cli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.midnightas.mlua.MLua;

public class MLuaCli {

	public String encodingToUse = StandardCharsets.UTF_8.name();
	public boolean piping;

	public MLuaCli(String[] args) {
		int i = 0;

		while (args[i].startsWith("-")) {
			switch (args[i].substring(1)) {
			case "p":
			case "-piping":
				piping = true;
				break;

			case "e":
			case "-encoding":
				encodingToUse = args[++i];
				break;

			default:
				error("Unknown argument " + args[i]);
				return;
			}

			i++;
			if(args.length == i)
				break;
		}

		if (!piping) {
			for (; i < args.length; i++) {
				compile(new File(args[i]));
			}
		} else {
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				int c;
				while((c = System.in.read()) != -1) {
					baos.write(c);
				}
				
				System.out.println(MLua.compile(baos.toString(encodingToUse)));
				System.out.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void compile(File file) {
		File resultFile = new File(Utils.removeExtension(file.getAbsolutePath()) + ".lua");

		try {
			String input = new String(Files.readAllBytes(file.toPath()), Charset.forName(encodingToUse));
			String output = MLua.compile(input);

			FileWriter outputWriter = new FileWriter(resultFile, false);
			outputWriter.write(output);
			outputWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			error("Invalid number of arguments.");
			return;
		}

		new MLuaCli(args);
	}

	public static void error(String error) {
		System.err.println(error);
		printHelp();
	}

	private static final String ARG_INFO_FORMAT = "\t%s | %s\t%s\t\t- %s\n";

	public static void printHelp() {
		String currentJarName = "mlua.jar";
		try {
			File file = new File(MLuaCli.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
			if (file.isFile())
				currentJarName = file.getName();
		} catch (URISyntaxException e) {
		}

		PrintStream s = System.err;

		s.println("Syntax:");
		s.println("\t./" + currentJarName + " [args] <inputs>");
		s.println();
		s.println("Supported args:");
		s.printf(ARG_INFO_FORMAT, "-e", "--encoding", "<enc>", "Encoding to use when reading the source files");
		s.printf(ARG_INFO_FORMAT, "-p", "--piping", "", "Pipe in a single source into stdin. Output will be pushed through stdout");
	}

}
