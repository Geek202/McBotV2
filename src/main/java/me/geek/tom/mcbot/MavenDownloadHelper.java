package me.geek.tom.mcbot;

import me.geek.tom.mcbot.mappings.MavenDownloader;

import java.io.File;
import java.util.Scanner;

public class MavenDownloadHelper {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter maven url:\n> ");
        String mavenUrl = scanner.nextLine();

        System.out.print("Enter group:\n> ");
        String group = scanner.nextLine();

        System.out.print("Enter name:\n> ");
        String name = scanner.nextLine();

        System.out.print("Enter version:\n> ");
        String version = scanner.nextLine();

        System.out.print("Enter classifier:\n> ");
        String classifier = scanner.nextLine();

        System.out.print("Enter extension:\n> ");
        String extension = scanner.nextLine();

        new MavenDownloader(new File("mavenStore"), mavenUrl).download(group, name, version, classifier, extension)
        .subscribe(f -> System.out.println("Downloaded to: " + f));
    }

}
