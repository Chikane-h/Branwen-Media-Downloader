import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class BranwenMediaDownloader extends JFrame {
    private JTextField ytDlpField;
    private JTextField ffmpegField;
    private JTextField saveDirField;
    private JTextField urlField;
    private JProgressBar progressBar;
    private JTextArea logArea;
    private Preferences config;
    private Process currentProcess;
    private JButton downloadBtn;
    private JButton cancelBtn;

    public BranwenMediaDownloader() {
        setTitle("Branwen Media Downloader");
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Load config from preferences
        config = Preferences.userNodeForPackage(BranwenMediaDownloader.class);

        // yt-dlp path
        gbc.gridx = 0;
        gbc.gridy = 0;
        add(new JLabel("yt-dlp path:"), gbc);
        gbc.gridx = 1;
        ytDlpField = new JTextField(config.get("ytDlpPath", ""), 30);
        add(ytDlpField, gbc);
        gbc.gridx = 2;
        JButton browseYt = new JButton("Browse");
        browseYt.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                browseFile(ytDlpField, "Select yt-dlp executable");
            }
        });
        add(browseYt, gbc);

        // ffmpeg path (optional)
        gbc.gridx = 0;
        gbc.gridy = 1;
        add(new JLabel("ffmpeg path (optional):"), gbc);
        gbc.gridx = 1;
        ffmpegField = new JTextField(config.get("ffmpegPath", ""), 30);
        add(ffmpegField, gbc);
        gbc.gridx = 2;
        JButton browseFf = new JButton("Browse");
        browseFf.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                browseFile(ffmpegField, "Select ffmpeg executable");
            }
        });
        add(browseFf, gbc);

        // save directory
        gbc.gridx = 0;
        gbc.gridy = 2;
        add(new JLabel("Save directory:"), gbc);
        gbc.gridx = 1;
        saveDirField = new JTextField(config.get("saveDir", ""), 30);
        add(saveDirField, gbc);
        gbc.gridx = 2;
        JButton browseSave = new JButton("Browse");
        browseSave.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                browseDir(saveDirField, "Select save directory");
            }
        });
        add(browseSave, gbc);

        // URL
        gbc.gridx = 0;
        gbc.gridy = 3;
        add(new JLabel("Video URL:"), gbc);
        gbc.gridx = 1;
        urlField = new JTextField(30);
        add(urlField, gbc);

        // Buttons
        JPanel buttonPanel = new JPanel();
        downloadBtn = new JButton("Download");
        downloadBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                download();
            }
        });
        buttonPanel.add(downloadBtn);

        cancelBtn = new JButton("Cancel");
        cancelBtn.setEnabled(false);
        cancelBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancelDownload();
            }
        });
        buttonPanel.add(cancelBtn);

        JButton updateYtBtn = new JButton("Update yt-dlp");
        updateYtBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateYtDlp();
            }
        });
        buttonPanel.add(updateYtBtn);

        JButton updateFfBtn = new JButton("Update ffmpeg");
        updateFfBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateFfmpeg();
            }
        });
        buttonPanel.add(updateFfBtn);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        add(buttonPanel, gbc);

        // Progress bar
        gbc.gridy = 5;
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        add(progressBar, gbc);

        // Log area
        gbc.gridy = 6;
        logArea = new JTextArea(10, 40);
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        add(scrollPane, gbc);

        pack();
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveConfig();
                System.exit(0);
            }
        });
        setVisible(true);
    }

    private void saveConfig() {
        config.put("ytDlpPath", ytDlpField.getText());
        config.put("ffmpegPath", ffmpegField.getText());
        config.put("saveDir", saveDirField.getText());
        try {
            config.sync();
        } catch (Exception ex) {
        }
    }

    private void browseFile(JTextField field, String title) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(title);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            field.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void browseDir(JTextField field, String title) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(title);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            field.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void download() {
        String ytPath = ytDlpField.getText();
        String ffPath = ffmpegField.getText();
        String save = saveDirField.getText();
        String url = urlField.getText();

        if (ytPath.isEmpty() || save.isEmpty() || url.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill required fields: yt-dlp path, save directory, and video URL.");
            return;
        }

        downloadBtn.setEnabled(false);
        cancelBtn.setEnabled(true);
        progressBar.setValue(0);
        logArea.setText("");

        new Thread(() -> {
            try {
                List<String> cmdList = new ArrayList<>();
                cmdList.add(ytPath);
                if (!ffPath.isEmpty()) {
                    cmdList.add("--ffmpeg-location");
                    cmdList.add(ffPath);
                }
                cmdList.add("-o");
                cmdList.add(save + "/%(title)s.%(ext)s");
                cmdList.add(url);

                ProcessBuilder pb = new ProcessBuilder(cmdList);
                pb.redirectErrorStream(true);
                currentProcess = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    log(line);
                    parseProgress(line);
                }
                currentProcess.waitFor();
                log("Download finished.");
            } catch (Exception ex) {
                log(ex.getMessage());
            } finally {
                currentProcess = null;
                SwingUtilities.invokeLater(() -> {
                    downloadBtn.setEnabled(true);
                    cancelBtn.setEnabled(false);
                });
            }
        }).start();
    }

    private void cancelDownload() {
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroy();
            log("Download cancelled.");
        }
    }

    private void updateYtDlp() {
        String ytPath = ytDlpField.getText();
        if (ytPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "yt-dlp path is empty.");
            return;
        }

        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(ytPath, "--update");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    log(line);
                }
                p.waitFor();
                log("yt-dlp update finished.");
            } catch (Exception ex) {
                log(ex.getMessage());
            }
        }).start();
    }

    private void updateFfmpeg() {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            JOptionPane.showMessageDialog(this, "Automatic ffmpeg update is only supported on Windows.");
            return;
        }

        String ffPath = ffmpegField.getText();
        File dir;
        if (ffPath.isEmpty()) {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Select directory to save ffmpeg");
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                dir = fc.getSelectedFile();
            } else {
                return;
            }
        } else {
            File ffFile = new File(ffPath);
            dir = ffFile.getParentFile();
        }

        new Thread(() -> {
            try {
                log("Downloading latest ffmpeg...");
                URL url = URI.create("https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                InputStream is = conn.getInputStream();
                File tempZip = new File(System.getProperty("java.io.tmpdir"), "ffmpeg.zip");
                FileOutputStream fos = new FileOutputStream(tempZip);
                byte[] buffer = new byte[4096];
                int len;
                while ((len = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
                is.close();

                log("Download complete. Extracting...");
                ZipInputStream zis = new ZipInputStream(new FileInputStream(tempZip));
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().contains("/bin/") && !entry.isDirectory()) {
                        String fileName = entry.getName().substring(entry.getName().lastIndexOf("/") + 1);
                        File outFile = new File(dir, fileName);
                        FileOutputStream out = new FileOutputStream(outFile);
                        while ((len = zis.read(buffer)) > 0) {
                            out.write(buffer, 0, len);
                        }
                        out.close();
                    }
                }
                zis.close();
                tempZip.delete();
                log("ffmpeg updated.");
            } catch (Exception ex) {
                log(ex.getMessage());
            }
        }).start();
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> logArea.append(msg + "\n"));
    }

    private void parseProgress(String line) {
        if (line.startsWith("[download]") && line.contains("%")) {
            try {
                String percPart = line.split("%")[0].trim();
                String[] parts = percPart.split("\\s+");
                String percStr = parts[parts.length - 1];
                double perc = Double.parseDouble(percStr);
                SwingUtilities.invokeLater(() -> progressBar.setValue((int) perc));
            } catch (Exception ignored) {
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(BranwenMediaDownloader::new);
    }
}