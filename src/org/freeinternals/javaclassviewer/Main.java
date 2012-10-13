/*
 * Main.java    23:11, Apr 07, 2009
 *
 * Copyright 2009, FreeInternals.org. All rights reserved.
 * Use is subject to license terms.
 */
package org.freeinternals.javaclassviewer;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.zip.ZipEntry;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.freeinternals.classfile.ui.JSplitPaneClassFile;
import org.freeinternals.classfile.ui.JTreeNodeZipFile;
import org.freeinternals.classfile.ui.JTreeZipFile;
import org.freeinternals.classfile.ui.Tool;
import org.freeinternals.common.ui.JFrameTool;
import org.freeinternals.common.ui.JPanelForTree;

/**
 *
 * @author Amos Shi
 * @since JDK 6.0
 */
public final class Main extends JFrame implements WindowListener {

    private static final long serialVersionUID = 4876543219876500000L;
    private JTreeZipFile zftree;
    private JPanelForTree zftreeContainer;
    private JSplitPaneClassFile cfPane;
    private final int maxRecentItemsCount = 10;
    private List<JMenuItem> recentItems = new ArrayList<JMenuItem>(maxRecentItemsCount);
    private JMenu menuFile;
    private File lastSelectedFile;

    private Main() {
        this.setTitle("Java Class Viewer");
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.addWindowListener(this);

        JFrameTool.centerJFrame(this);
        this.createMenu();
        this.setVisible(true);
    }

    private void createMenu() {
        final JMenuBar menuBar = new JMenuBar();
        this.setJMenuBar(menuBar);

        // File
        menuFile = new JMenu("File");
        menuFile.setMnemonic(KeyEvent.VK_F);
        menuBar.add(menuFile);

        // File --> Open
        final JMenuItem menuItem_FileOpen = new JMenuItem("Open...");
        menuItem_FileOpen.setMnemonic(KeyEvent.VK_O);
        menuItem_FileOpen.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_O,
                ActionEvent.CTRL_MASK));
        menuItem_FileOpen.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(final ActionEvent e) {
                menu_FileOpen();
            }
        });
        menuFile.add(menuItem_FileOpen);

        // File --> Close
        final JMenuItem menuItem_FileClose = new JMenuItem("Close");
        menuItem_FileClose.setMnemonic(KeyEvent.VK_C);
        menuItem_FileClose.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(final ActionEvent e) {
                menu_FileClose();
            }
        });
        menuFile.add(menuItem_FileClose);
        
        //Recent files
        menuFile.addSeparator();
        for (int i = 0; i < maxRecentItemsCount; i++) {
            Preferences pref = Preferences.userNodeForPackage(Main.class);
            String recentFilePath = pref.get("recent" + i, null);
            if (recentFilePath != null) {
                JMenuItem recentFileMenuItem = new JMenuItem(recentFilePath);
                addRecentFileActionListener(recentFileMenuItem);
                recentItems.add(recentFileMenuItem);
                menuFile.add(recentFileMenuItem);
            }
        }

        // Help
        final JMenu menuHelp = new JMenu("Help");
        menuFile.setMnemonic(KeyEvent.VK_H);
        menuBar.add(menuHelp);

        // Help --> About
        final JMenuItem menuItem_HelpAbout = new JMenuItem("About");
        menuItem_HelpAbout.setMnemonic(KeyEvent.VK_A);
        menuItem_HelpAbout.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(final ActionEvent e) {
                menu_HelpAbout();
            }
        });
        menuHelp.add(menuItem_HelpAbout);

    }

    private void menu_FileOpen() {
        final FileNameExtensionFilter filterSupported = new FileNameExtensionFilter("java files (*.jar, *.class)", "jar", "class");
        final JFileChooser chooser = new JFileChooser();
        if (lastSelectedFile!=null) {
            chooser.setSelectedFile(lastSelectedFile);
        }
        chooser.addChoosableFileFilter(filterSupported);

        final int returnVal = chooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            final File file = chooser.getSelectedFile();
            this.lastSelectedFile = file;
            open_File(file);
        }
    }
    
    private void addToRecent(String next) {
        for (JMenuItem jMenuItem : recentItems) {
            menuFile.remove(jMenuItem);
        }
        boolean find = false;
        for (int i = 0; i < recentItems.size(); i++) {
            JMenuItem jMenuItem = recentItems.get(i);
            if (jMenuItem.getText().equals(next)) {
                recentItems.remove(i);
                recentItems.add(jMenuItem);
                find = true;
                break;
            }
        }
        if (!find) {
            if (recentItems.size() == maxRecentItemsCount) {
                recentItems.remove(0);
            }
            final JMenuItem item = new JMenuItem(next);
            recentItems.add(item);
            addRecentFileActionListener(item);
        }
        for (JMenuItem jMenuItem : recentItems) {
            menuFile.insert(jMenuItem, 3);
        }
    }

    private void open_File(final File file) {
        this.clearContent();
        final String name = file.getName();
        if (name.endsWith(".jar")) {
            addToRecent(file.getAbsolutePath());
            this.open_JarFile(file);
        } else if (name.endsWith(".class")) {
            addToRecent(file.getAbsolutePath());
            this.open_ClassFile(file);
        } else {
            JFrameTool.showMessage(
                    this,
                    String.format("Un-supported file type. Please select a '.jar' or '.class' file. \nFile: %s", file.getPath()),
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void open_JarFile(final File file) {
        try {
            this.zftree = new JTreeZipFile(new JarFile(
                    file,
                    false,
                    JarFile.OPEN_READ));
            this.zftree.addMouseListener(new MouseAdapter() {

                @Override
                public void mousePressed(final MouseEvent e) {
                    if (e.getClickCount() != 2) {
                        return;
                    }
                    if (zftree.getRowForLocation(e.getX(), e.getY()) == -1) {
                        return;
                    }

                    zftree_DoubleClick(zftree.getPathForLocation(e.getX(), e.getY()));
                }
            });
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            JFrameTool.showMessage(
                    this,
                    ex.toString(),
                    JOptionPane.ERROR_MESSAGE);
        }

        if (this.zftree != null) {
            this.zftreeContainer = new JPanelForTree(this.zftree);
            this.add(this.zftreeContainer, BorderLayout.CENTER);

            this.resizeForContent();
        }
    }

    private void open_ClassFile(final File file) {
        this.cfPane = new JSplitPaneClassFile(Tool.readClassFile(file));
        this.add(this.cfPane, BorderLayout.CENTER);

        this.resizeForContent();
    }

    private void resizeForContent() {
        this.setSize(this.getWidth() + 2, this.getHeight());
        this.setSize(this.getWidth() - 2, this.getHeight());
    }

    private void menu_FileClose() {
        this.clearContent();
        this.setSize(this.getWidth() - 1, this.getHeight());
    }

    private void menu_HelpAbout() {
        final JDialogAbout about = new JDialogAbout(this, "About");
        about.setLocationRelativeTo(this);
        about.setVisible(true);
    }

    private void clearContent() {
        if (this.zftreeContainer != null) {
            this.remove(this.zftreeContainer);
            this.validate();
        }
        this.zftreeContainer = null;
        this.zftree = null;

        if (this.cfPane != null) {
            this.remove(this.cfPane);
            this.validate();
        }
        this.cfPane = null;
    }

    private void zftree_DoubleClick(final TreePath tp) {
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode) this.zftree.getLastSelectedPathComponent();
        if (node == null) {
            return;
        }
        if (node.isLeaf() == false) {
            return;
        }

        final Object objLast = tp.getLastPathComponent();
        if (objLast == null) {
            return;
        }

        if (objLast.toString().endsWith(".class") == false) {
            return;
        }

        final Object[] objArray = tp.getPath();
        if (objArray.length < 2) {
            return;
        }

        final Object userObj = node.getUserObject();
        if (!(userObj instanceof JTreeNodeZipFile)) {
            return;
        }

        final ZipEntry ze = ((JTreeNodeZipFile) userObj).getNodeObject();
        if (ze == null) {
            JFrameTool.showMessage(
                    this,
                    "Node Object [zip entry] is emtpy.",
                    JOptionPane.WARNING_MESSAGE);
        } else {
            this.showClassWindow(ze);
        }
    }

    private void showClassWindow(final ZipEntry ze) {

        final byte b[];
        try {
            b = Tool.readClassFile(zftree.getZipFile(), ze);
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            JFrameTool.showMessage(
                    this,
                    String.format("Read the class file failed.\n%s", ex.getMessage()),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        final StringBuffer sbTitle = new StringBuffer();
        sbTitle.append(this.zftree.getZipFile().getName());
        sbTitle.append(" - ");
        sbTitle.append(ze.getName());

        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                new JFrameClassFile(
                        sbTitle.toString(),
                        b).setVisible(true);
            }
        });
    }

    /**
     * @param args the command line arguments
     */
    public static void main(final String[] args) {

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedLookAndFeelException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                new Main().setVisible(true);
            }
        });
    }
    
    private void addRecentFileActionListener(JMenuItem item) {
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                open_File(new File(e.getActionCommand()));
            }
        });
    }

    @Override
    public void windowClosing(WindowEvent e) {
        Preferences pref = Preferences.userNodeForPackage(Main.class);
        for (int i = 0; i < recentItems.size(); i++) {
            pref.put("recent" + i, recentItems.get(i).getText());
        }
        try {
            pref.sync();
        } catch (BackingStoreException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void windowOpened(WindowEvent e) {}
    public void windowClosed(WindowEvent e) {}
    public void windowIconified(WindowEvent e) {}
    public void windowDeiconified(WindowEvent e) {}
    public void windowActivated(WindowEvent e) {}
    public void windowDeactivated(WindowEvent e) {}
}