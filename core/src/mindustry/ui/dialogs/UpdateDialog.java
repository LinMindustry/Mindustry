package mindustry.ui.dialogs;

import arc.Core;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;

import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.io.*;

import java.net.HttpURLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class UpdateDialog extends FloatingDialog{

    private Label log = new Label("null");
    private Label process = new Label("null");
    private Runnable task;
    private Runnable downloader;

    public UpdateDialog(){
        super("Update");
        cont.clear();
        cont.defaults().size(500f, 400f);
        Table logTable = new Table();
        logTable.add(log);
        logTable.row();
        logTable.add(process);
        cont.add(logTable);
        cont.row();
        addCloseButton();
        addOkButton(task);
        setFillParent(false);
    }

    public void show(String updateLog, String downloadlink) {
        log.clear();
        log.setText(updateLog);
        process.clear();
        process.setText("Waiting..... Path: " + System.getProperty("user.dir"));
        downloader = () -> {
            try {
                URL website = new URL(downloadlink);
                ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                FileOutputStream fos = new FileOutputStream(System.getProperty("user.dir") + "/temp");
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                // download done
                File oldMind = new File(System.getProperty("user.dir") + "/Mindustry.jar");
                oldMind.delete();
                File newMind = new File(System.getProperty("user.dir") + "/temp");
                newMind.renameTo(oldMind);
                process.setText("Done");
            }
            catch (IOException e) {
                // handles IO exceptions
                process.setText("Download Failed :/");
                System.out.println("Exception: " + e);
            }
        };
        task = () -> {
            process.setText("Downloading -> Path: " + System.getProperty("user.dir"));
            new Thread(downloader).start();
            Core.app.exit();
        };
        buttons.clear();
        addCloseButton();
        addOkButton(task);
        show();
    }
}