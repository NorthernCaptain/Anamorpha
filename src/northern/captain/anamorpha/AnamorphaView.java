/*
 * AnamorphaView.java
 */
package northern.captain.anamorpha;

import java.awt.Image;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jdesktop.application.Action;
import org.jdesktop.application.ResourceMap;
import org.jdesktop.application.SingleFrameApplication;
import org.jdesktop.application.FrameView;
import org.jdesktop.application.TaskMonitor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.Timer;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import northern.captain.anamorph.Anamorph;
import org.jdesktop.application.Application;
import org.jdesktop.application.ApplicationContext;
import org.jdesktop.application.Task;
import org.jdesktop.application.TaskService;

/**
 * The application's main frame.
 */
public class AnamorphaView extends FrameView
{

    private String srcPath;
    private String dstPath;

    public AnamorphaView(SingleFrameApplication app)
    {
        super(app);

        initComponents();

        // status bar initialization - message timeout, idle icon and busy animation, etc
        ResourceMap resourceMap = getResourceMap();
        int messageTimeout = resourceMap.getInteger("StatusBar.messageTimeout");
        messageTimer = new Timer(messageTimeout, new ActionListener()
        {

            public void actionPerformed(ActionEvent e)
            {
                statusMessageLabel.setText("");
            }
        });
        messageTimer.setRepeats(false);
        int busyAnimationRate = resourceMap.getInteger("StatusBar.busyAnimationRate");
        for (int i = 0; i < busyIcons.length; i++)
        {
            busyIcons[i] = resourceMap.getIcon("StatusBar.busyIcons[" + i + "]");
        }
        busyIconTimer = new Timer(busyAnimationRate, new ActionListener()
        {

            public void actionPerformed(ActionEvent e)
            {
                busyIconIndex = (busyIconIndex + 1) % busyIcons.length;
                statusAnimationLabel.setIcon(busyIcons[busyIconIndex]);
            }
        });
        idleIcon = resourceMap.getIcon("StatusBar.idleIcon");
        statusAnimationLabel.setIcon(idleIcon);
        progressBar.setVisible(false);

        // connecting action tasks to status bar via TaskMonitor
        TaskMonitor taskMonitor = new TaskMonitor(getApplication().getContext());
        taskMonitor.addPropertyChangeListener(new java.beans.PropertyChangeListener()
        {

            public void propertyChange(java.beans.PropertyChangeEvent evt)
            {
                String propertyName = evt.getPropertyName();
                if ("started".equals(propertyName))
                {
                    if (!busyIconTimer.isRunning())
                    {
                        statusAnimationLabel.setIcon(busyIcons[0]);
                        busyIconIndex = 0;
                        busyIconTimer.start();
                    }
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(true);
                } else if ("done".equals(propertyName))
                {
                    busyIconTimer.stop();
                    statusAnimationLabel.setIcon(idleIcon);
                    progressBar.setVisible(false);
                    progressBar.setValue(0);
                } else if ("message".equals(propertyName))
                {
                    String text = (String) (evt.getNewValue());
                    statusMessageLabel.setText((text == null) ? "" : text);
                    messageTimer.restart();
                } else if ("progress".equals(propertyName))
                {
                    int value = (Integer) (evt.getNewValue());
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(value);
                }
            }
        });
    }

    @Action
    public void showAboutBox()
    {
        if (aboutBox == null)
        {
            JFrame mainFrame = AnamorphaApp.getApplication().getMainFrame();
            aboutBox = new AnamorphaAboutBox(mainFrame);
            aboutBox.setLocationRelativeTo(mainFrame);
        }
        AnamorphaApp.getApplication().show(aboutBox);
    }

    @Action
    public void openSourceFile()
    {
        JFileChooser openDialog = new JFileChooser();
        if (openDialog.showOpenDialog(mainPanel) == JFileChooser.APPROVE_OPTION)
        {
            File src = openDialog.getSelectedFile();
            ImageIcon icon = new ImageIcon(src.getAbsolutePath());
            Image img = icon.getImage();
            if (icon.getIconWidth() <= 0)
            {
                imagePreview.setText("Ошибка: Невозможно загрузить изображение");
                return;
            }
            imagePreview.setText("");

            if(icon.getIconWidth() > icon.getIconHeight())
            {
                if(icon.getIconWidth() > maxWidth)
                    img = img.getScaledInstance(maxWidth, -1, Image.SCALE_SMOOTH);
            }
            else
            {
                if(icon.getIconHeight() > maxWidth)
                    img = img.getScaledInstance(-1, maxWidth, Image.SCALE_SMOOTH);                
            }
            icon.setImage(img);
            imagePreview.setIcon(icon);
            setSrcPath(src.getAbsolutePath());

        }
    }

    private void detectMaxWidth()
    {
        int w = previewPanel.getWidth();
        int h = previewPanel.getHeight();
        if(w <= 100 || h <= 100)
        {
            maxWidth = 500;
            return;
        }
        
        int min = w > h ? h : w;
        maxWidth = min * 900 / 1000;
    }
    
    private void setSrcPath(String path)
    {
        srcPath = path;
        srcFileLbl.setText(path);
    }

    private void setDstPath(String path)
    {
        dstPath = path;
        dstFileEdit.setText(path);
    }

    @Action
    public void chooseDestFile()
    {
        JFileChooser openDialog = new JFileChooser();
        if (openDialog.showSaveDialog(mainPanel) == JFileChooser.APPROVE_OPTION)
        {
            File dst = openDialog.getSelectedFile();
            setDstPath(dst.getAbsolutePath());
        }
    }

    public void barMessage(String text)
    {
        statusMessageLabel.setText((text == null) ? "" : text);
        messageTimer.restart();
    }

    @Action
    public void doPreview()
    {
        needSave = false;
        doAnamorphInternal();
    }
    
    private boolean needSave = false;
    
    @Action
    public void doAnamorpth()
    {
        dstPath = dstFileEdit.getText().trim();
        if (dstPath.isEmpty())
        {
            barMessage("Ошибка: Не указан файл результата!");
            return;
        }
        needSave = true;
        doAnamorphInternal();
    }
    
    private int maxWidth = 500;
    
    private void doAnamorphInternal()
    {
        srcPath = srcFileLbl.getText().trim();
        if (srcPath.isEmpty())
        {
            barMessage("Ошибка: не указан исходный файл!");
            return;
        }

        previewButton.setEnabled(false);
        doButton.setEnabled(false);        

        dstPath = dstFileEdit.getText().trim();

        final int destWidth = (Integer) widthSpin.getValue();
        final int angle = (Integer) angleSpin.getValue();
        final int diameter = (Integer) diameterSpin.getValue();

        doButton.setEnabled(false);

        getApplication().getContext().getTaskService().execute(new Task(getApplication())
        {

            @Override
            protected Object doInBackground() throws Exception
            {
                BufferedImage srcImage = null;
                setMessage("Загрузка изображения...");
                setProgress(0.0f);
                try
                {
                    srcImage = ImageIO.read(new File(srcPath));
                } catch (IOException e)
                {
                    setMessage("Ошибка чтения файла: " + e.getLocalizedMessage());
                    return 0;
                }

                setMessage("Анаморфинг...");
                setProgress(0.2f);
                Anamorph anamorph = new Anamorph(srcImage);

                BufferedImage destImage = anamorph.doMorph(new Anamorph.Params(diameter/2, destWidth * 100 / 105, angle/2));
                
                if(needSave)
                {
                    setMessage("Построение...");
                    setProgress(0.5f);

                    destImage = resampleImage(destImage);
                    
                    setMessage("Запись в файл...");
                    setProgress(0.8f);

                    try
                    {
                        String extention = dstPath;
                        int idx = extention.lastIndexOf(".");
                        if (idx > 0)
                        {
                            extention = extention.substring(idx + 1);
                        } else
                        {
                            extention = "png";
                        }

                        
                        ImageIO.write(destImage, extention, new File(dstPath));
                    } catch (IOException e)
                    {
                        setMessage("Ошибка ЗАПИСИ файла: " + e.getLocalizedMessage());
                        return 0;
                    }
                    setMessage("Результат сохранен в " + dstPath);
                } else
                {
                    setMessage("Удачно");                    
                }
                
                Image preview = destImage.getScaledInstance(maxWidth, maxWidth, Image.SCALE_SMOOTH);
                
                setProgress(1.0f);
                return preview;
            }

            private void sleep(int millis)
            {
                try
                {
                    Thread.sleep(millis);
                } catch (InterruptedException ex)
                {
                }
            }

            @Override
            protected void succeeded(Object result)
            {
                if(result instanceof Image)
                {
                    Image preview = (Image)result;
                    imagePreview.setIcon(new ImageIcon(preview));
                }
            }

            @Override
            protected void finished()
            {
                doButton.setEnabled(true);
                previewButton.setEnabled(true);
            }
        });
    }

    private BufferedImage resampleImage(BufferedImage before)
    {
        int w = before.getWidth();
        int h = before.getHeight();
        BufferedImage after = new BufferedImage(w * 105 / 100, h * 105 / 100, BufferedImage.TYPE_INT_ARGB);
        AffineTransform at = new AffineTransform();
        at.scale(1.05, 1.05);
        AffineTransformOp scaleOp = 
           new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
        after = scaleOp.filter(before, after);        
        return after;
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainPanel = new javax.swing.JPanel();
        previewPanel = new javax.swing.JPanel();
        imagePreview = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        doButton = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        widthSpin = new javax.swing.JSpinner();
        jLabel4 = new javax.swing.JLabel();
        angleSpin = new javax.swing.JSpinner();
        jLabel5 = new javax.swing.JLabel();
        diameterSpin = new javax.swing.JSpinner();
        previewButton = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        srcFileLbl = new javax.swing.JLabel();
        jButton1 = new javax.swing.JButton();
        jLabel3 = new javax.swing.JLabel();
        dstFileEdit = new javax.swing.JTextField();
        jButton2 = new javax.swing.JButton();
        menuBar = new javax.swing.JMenuBar();
        javax.swing.JMenu fileMenu = new javax.swing.JMenu();
        openFileMenuItem = new javax.swing.JMenuItem();
        javax.swing.JMenuItem exitMenuItem = new javax.swing.JMenuItem();
        javax.swing.JMenu helpMenu = new javax.swing.JMenu();
        javax.swing.JMenuItem aboutMenuItem = new javax.swing.JMenuItem();
        statusPanel = new javax.swing.JPanel();
        javax.swing.JSeparator statusPanelSeparator = new javax.swing.JSeparator();
        statusMessageLabel = new javax.swing.JLabel();
        statusAnimationLabel = new javax.swing.JLabel();
        progressBar = new javax.swing.JProgressBar();

        mainPanel.setName("mainPanel"); // NOI18N

        previewPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        previewPanel.setName("previewPanel"); // NOI18N

        org.jdesktop.application.ResourceMap resourceMap = org.jdesktop.application.Application.getInstance(northern.captain.anamorpha.AnamorphaApp.class).getContext().getResourceMap(AnamorphaView.class);
        imagePreview.setFont(resourceMap.getFont("imagePreview.font")); // NOI18N
        imagePreview.setForeground(resourceMap.getColor("imagePreview.foreground")); // NOI18N
        imagePreview.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        imagePreview.setIcon(resourceMap.getIcon("imagePreview.icon")); // NOI18N
        imagePreview.setText(resourceMap.getString("imagePreview.text")); // NOI18N
        imagePreview.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        imagePreview.setName("imagePreview"); // NOI18N
        imagePreview.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);

        javax.swing.GroupLayout previewPanelLayout = new javax.swing.GroupLayout(previewPanel);
        previewPanel.setLayout(previewPanelLayout);
        previewPanelLayout.setHorizontalGroup(
            previewPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(imagePreview, javax.swing.GroupLayout.DEFAULT_SIZE, 549, Short.MAX_VALUE)
        );
        previewPanelLayout.setVerticalGroup(
            previewPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(imagePreview, javax.swing.GroupLayout.DEFAULT_SIZE, 245, Short.MAX_VALUE)
        );

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder(resourceMap.getString("jPanel2.border.title"))); // NOI18N
        jPanel2.setName("jPanel2"); // NOI18N

        javax.swing.ActionMap actionMap = org.jdesktop.application.Application.getInstance(northern.captain.anamorpha.AnamorphaApp.class).getContext().getActionMap(AnamorphaView.class, this);
        doButton.setAction(actionMap.get("doAnamorpth")); // NOI18N
        doButton.setIcon(resourceMap.getIcon("doButton.icon")); // NOI18N
        doButton.setText(resourceMap.getString("doButton.text")); // NOI18N
        doButton.setName("doButton"); // NOI18N

        jLabel2.setText(resourceMap.getString("jLabel2.text")); // NOI18N
        jLabel2.setName("jLabel2"); // NOI18N

        widthSpin.setModel(new javax.swing.SpinnerNumberModel(1200, 600, 7000, 50));
        widthSpin.setName("widthSpin"); // NOI18N

        jLabel4.setText(resourceMap.getString("jLabel4.text")); // NOI18N
        jLabel4.setName("jLabel4"); // NOI18N

        angleSpin.setModel(new javax.swing.SpinnerNumberModel(120, 0, 240, 1));
        angleSpin.setName("angleSpin"); // NOI18N

        jLabel5.setText(resourceMap.getString("jLabel5.text")); // NOI18N
        jLabel5.setName("jLabel5"); // NOI18N

        diameterSpin.setModel(new javax.swing.SpinnerNumberModel(260, 20, 5500, 20));
        diameterSpin.setName("diameterSpin"); // NOI18N

        previewButton.setAction(actionMap.get("doPreview")); // NOI18N
        previewButton.setIcon(resourceMap.getIcon("previewButton.icon")); // NOI18N
        previewButton.setText(resourceMap.getString("previewButton.text")); // NOI18N
        previewButton.setName("previewButton"); // NOI18N

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(previewButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 252, Short.MAX_VALUE)
                    .addComponent(doButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 252, Short.MAX_VALUE)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel2)
                            .addComponent(jLabel4)
                            .addComponent(jLabel5))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(diameterSpin, javax.swing.GroupLayout.DEFAULT_SIZE, 95, Short.MAX_VALUE)
                            .addComponent(angleSpin, javax.swing.GroupLayout.DEFAULT_SIZE, 95, Short.MAX_VALUE)
                            .addComponent(widthSpin, javax.swing.GroupLayout.DEFAULT_SIZE, 95, Short.MAX_VALUE))))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(widthSpin, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(angleSpin, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(diameterSpin, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 125, Short.MAX_VALUE)
                .addComponent(previewButton, javax.swing.GroupLayout.PREFERRED_SIZE, 42, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(doButton, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder(resourceMap.getString("jPanel3.border.title"))); // NOI18N
        jPanel3.setName("jPanel3"); // NOI18N

        jLabel1.setText(resourceMap.getString("jLabel1.text")); // NOI18N
        jLabel1.setName("jLabel1"); // NOI18N

        srcFileLbl.setText(resourceMap.getString("srcFileLbl.text")); // NOI18N
        srcFileLbl.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        srcFileLbl.setName("srcFileLbl"); // NOI18N

        jButton1.setAction(actionMap.get("openSourceFile")); // NOI18N
        jButton1.setText(resourceMap.getString("jButton1.text")); // NOI18N
        jButton1.setName("jButton1"); // NOI18N

        jLabel3.setText(resourceMap.getString("jLabel3.text")); // NOI18N
        jLabel3.setName("jLabel3"); // NOI18N

        dstFileEdit.setText(resourceMap.getString("dstFileEdit.text")); // NOI18N
        dstFileEdit.setName("dstFileEdit"); // NOI18N

        jButton2.setAction(actionMap.get("chooseDestFile")); // NOI18N
        jButton2.setText(resourceMap.getString("jButton2.text")); // NOI18N
        jButton2.setName("jButton2"); // NOI18N

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(18, 18, 18)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(srcFileLbl, javax.swing.GroupLayout.DEFAULT_SIZE, 357, Short.MAX_VALUE)
                    .addComponent(dstFileEdit, javax.swing.GroupLayout.DEFAULT_SIZE, 357, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jButton2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jButton1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel1)
                        .addComponent(srcFileLbl, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jButton1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel3)
                        .addComponent(dstFileEdit, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jButton2))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jLabel3.getAccessibleContext().setAccessibleName(resourceMap.getString("jLabel3.AccessibleContext.accessibleName")); // NOI18N

        javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
        mainPanel.setLayout(mainPanelLayout);
        mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(previewPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        mainPanelLayout.setVerticalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(previewPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );

        menuBar.setName("menuBar"); // NOI18N

        fileMenu.setText(resourceMap.getString("fileMenu.text")); // NOI18N
        fileMenu.setName("fileMenu"); // NOI18N

        openFileMenuItem.setAction(actionMap.get("openSourceFile")); // NOI18N
        openFileMenuItem.setText(resourceMap.getString("openFileMenuItem.text")); // NOI18N
        openFileMenuItem.setName("openFileMenuItem"); // NOI18N
        fileMenu.add(openFileMenuItem);

        exitMenuItem.setAction(actionMap.get("quit")); // NOI18N
        exitMenuItem.setText(resourceMap.getString("exitMenuItem.text")); // NOI18N
        exitMenuItem.setName("exitMenuItem"); // NOI18N
        fileMenu.add(exitMenuItem);

        menuBar.add(fileMenu);

        helpMenu.setText(resourceMap.getString("helpMenu.text")); // NOI18N
        helpMenu.setName("helpMenu"); // NOI18N

        aboutMenuItem.setAction(actionMap.get("showAboutBox")); // NOI18N
        aboutMenuItem.setName("aboutMenuItem"); // NOI18N
        helpMenu.add(aboutMenuItem);

        menuBar.add(helpMenu);

        statusPanel.setName("statusPanel"); // NOI18N

        statusPanelSeparator.setName("statusPanelSeparator"); // NOI18N

        statusMessageLabel.setFont(resourceMap.getFont("statusMessageLabel.font")); // NOI18N
        statusMessageLabel.setForeground(resourceMap.getColor("statusMessageLabel.foreground")); // NOI18N
        statusMessageLabel.setName("statusMessageLabel"); // NOI18N

        statusAnimationLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        statusAnimationLabel.setName("statusAnimationLabel"); // NOI18N

        progressBar.setName("progressBar"); // NOI18N

        javax.swing.GroupLayout statusPanelLayout = new javax.swing.GroupLayout(statusPanel);
        statusPanel.setLayout(statusPanelLayout);
        statusPanelLayout.setHorizontalGroup(
            statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(statusPanelSeparator, javax.swing.GroupLayout.DEFAULT_SIZE, 867, Short.MAX_VALUE)
            .addGroup(statusPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(statusMessageLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 697, Short.MAX_VALUE)
                .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(statusAnimationLabel)
                .addContainerGap())
        );
        statusPanelLayout.setVerticalGroup(
            statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(statusPanelLayout.createSequentialGroup()
                .addComponent(statusPanelSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, 2, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(statusMessageLabel)
                    .addComponent(statusAnimationLabel)
                    .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(3, 3, 3))
        );

        setComponent(mainPanel);
        setMenuBar(menuBar);
        setStatusBar(statusPanel);
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSpinner angleSpin;
    private javax.swing.JSpinner diameterSpin;
    private javax.swing.JButton doButton;
    private javax.swing.JTextField dstFileEdit;
    private javax.swing.JLabel imagePreview;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JMenuItem openFileMenuItem;
    private javax.swing.JButton previewButton;
    private javax.swing.JPanel previewPanel;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JLabel srcFileLbl;
    private javax.swing.JLabel statusAnimationLabel;
    private javax.swing.JLabel statusMessageLabel;
    private javax.swing.JPanel statusPanel;
    private javax.swing.JSpinner widthSpin;
    // End of variables declaration//GEN-END:variables
    private final Timer messageTimer;
    private final Timer busyIconTimer;
    private final Icon idleIcon;
    private final Icon[] busyIcons = new Icon[15];
    private int busyIconIndex = 0;
    private JDialog aboutBox;
}
