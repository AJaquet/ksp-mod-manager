package llorx.kspModManager;

import javax.swing.*;
import javax.swing.border.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import java.awt.Component;
import java.awt.Color;
import java.awt.event.*;
import java.awt.Font;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;

import java.text.SimpleDateFormat;
import java.util.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.*;

import java.io.File;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.net.URL;
import java.net.URI;
import java.net.HttpURLConnection;
import java.net.URLEncoder;

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import static java.nio.file.FileVisitResult.*;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;

class MyTableModel extends AbstractTableModel {
	
	private List<Mod> mods;
	
	public MyTableModel(List<Mod> mods) {
		this.mods = mods;
	}
	
	@Override
	public int getRowCount() {
		return mods.size();
	}
	
	@Override
	public int getColumnCount() {
		return 2;
	}
	
	@Override
	public String getColumnName(int column) {
		String name = "??";
		switch (column) {
			case 0:
				name = "Mod name";
				break;
			case 1:
				name = "Installed version";
				break;
		}
		return name;
	}
	
	@Override
	public Class<?> getColumnClass(int columnIndex) {
		Class type = String.class;
		switch (columnIndex) {
			case 0:
			case 1:
				type = String.class;
				break;
		}
		return type;
	}
	
	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		return mods.get(rowIndex);
	}
}

class IconTextCellRenderer extends DefaultTableCellRenderer {
	
	ImageIcon install = new ImageIcon(getClass().getResource("/images/install.png"));
	ImageIcon online = new ImageIcon(getClass().getResource("/images/link.gif"));
	
	@Override
	public Component getTableCellRendererComponent(JTable table,
			Object value,
			boolean isSelected,
			boolean hasFocus,
			int row,
			int column) {
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		Mod mod = (Mod)value;
		
		switch (column) {
			case 0:
				if (mod.isInstallable()) {
					setIcon(install);
				} else {
					setIcon(online);
				}
				setText(mod.getName());
				break;
			case 1:
				setIcon(null);
				Font font = getFont();
				setFont(font.deriveFont(font.getStyle() | Font.BOLD));
				if (!mod.getStatus().equals("")) {
					setText(mod.getStatus());
				} else {
					if (mod.justUpdated == true) {
						setIcon(online);
					} else {
						setFont(font.deriveFont(font.getStyle() & ~Font.BOLD));
					}
					setText((mod.justUpdated == true?"[New version"+(mod.isInstallable()?" installed":"")+"] ":"") + mod.getVersion());
				}
				break;
		}
		return this;
	}
}

public class Main extends JFrame implements ActionListener {
	JButton downloadBut;
	JButton installBut;
	
	JButton configBut;
	
	JButton mmButton;
	JButton updateBut;
	
	Document xmlDoc;
	Element rootElement;
	
	List<Mod> modList = new ArrayList<Mod>();
	JTable mainList;
	
	Mod moduleManagerMod = null;
	
	Thread asyncDThread = null;
	
	List<Mod> modQeue = new ArrayList<Mod>();
	List<Mod> modInstallQeue = new ArrayList<Mod>();
	
	Object lock = new Object();
	
	ManagerConfig config = new ManagerConfig();
	
	boolean closingApp = false;
	
	public Main() {
		setLayout(null);
		
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent winEvt) {
				closingApp = true;
				if (asyncDThread != null) {
					while (asyncDThread != null && asyncDThread.isAlive()) {
						try {
							Thread.sleep(1000);
						} catch(Exception e) {
						}
					}
				}
				if ((new File("temp")).exists()) {
					DirIO.clearDir("temp");
				}
			}
		});
		
		Border line = BorderFactory.createLineBorder(Color.DARK_GRAY);
		Border empty = new EmptyBorder(1, 1, 1, 1);
		CompoundBorder border = new CompoundBorder(line, empty);
		
		configBut=new JButton("Config");
		configBut.setBounds(2,2,150,40);
		add(configBut);
		configBut.addActionListener(this);
		
		downloadBut=new JButton("[+] Download mod");
		downloadBut.setBounds(242,2,250,19);
		add(downloadBut);
		downloadBut.addActionListener(this);
		
		installBut=new JButton("Install Queued mods");
		installBut.setBounds(242,23,250,19);
		add(installBut);
		installBut.addActionListener(this);
		installBut.setEnabled(false);
		
		mmButton=new JButton("Download Module Manager");
		mmButton.setBounds(2,450,250,20);
		add(mmButton);
		mmButton.addActionListener(this);
		
		updateBut=new JButton("Check mod updates");
		updateBut.setBounds(340,450,150,20);
		add(updateBut);
		updateBut.addActionListener(this);
		
		mainList = new JTable(new MyTableModel(modList));
		JScrollPane barraDesplazamiento = new JScrollPane(mainList); 
		barraDesplazamiento.setBounds(2,47,490,400);
		add(barraDesplazamiento);
		mainList.getTableHeader().setReorderingAllowed(false);
		
		IconTextCellRenderer cellRenderer = new IconTextCellRenderer();
		mainList.setDefaultRenderer(Object.class, cellRenderer);
		
		mainList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				int r = mainList.rowAtPoint(e.getPoint());
				if (r >= 0 && r < mainList.getRowCount()) {
					mainList.setRowSelectionInterval(r, r);
				} else {
					mainList.clearSelection();
				}

				int rowindex = mainList.getSelectedRow();
				if (rowindex < 0)
					return;
				if (e.isPopupTrigger() && e.getComponent() instanceof JTable ) {
					JPopupMenu popup = new MyPopMenu(getSelectedMod());
					popup.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		});
	}
	
	Mod getSelectedMod() {
		synchronized(lock) {
			if (mainList.getSelectedRow() > -1) {
				return modList.get(mainList.getSelectedRow());
			}
		}
		return null;
	}
	
	void renameSelectedMod() {
		synchronized(lock) {
			Mod mod = getSelectedMod();
			if (mod != null && mod.getStatus().equals("")) {
				String newName = JOptionPane.showInputDialog(null, "What is the new name?", mod.getName());
				if (newName != null && newName.length() > 0) {
					mod.setName(newName);
					setMod(mod);
					saveConfigFile();
				}
			}
		}
	}
	
	void updateSelectedMod() {
		synchronized(lock) {
			Mod mod = getSelectedMod();
			if (mod != null && mod.getStatus().equals("")) {
				List<Mod> list = new ArrayList<Mod>();
				list.add(mod);
				updateMods(list);
			}
		}
	}
	
	void reinstallSelectedMod() {
		synchronized(lock) {
			Mod mod = getSelectedMod();
			if (mod != null && mod.getStatus().equals("")) {
				mod.setInstallable(true);
				List<Mod> list = new ArrayList<Mod>();
				list.add(mod);
				updateMods(list, true);
			}
		}
	}
	
	void removeSelectedMod() {
		synchronized(lock) {
			Mod mod = getSelectedMod();
			if (mod != null && mod.getStatus().equals("")) {
				int reply;
				if (mod.isInstallable() == false) {
					reply = JOptionPane.showConfirmDialog(null, "Do you want to remove the mod from the list?", "Delete Mod", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
				} else {
					reply = JOptionPane.showOptionDialog(null, "Do you want to delete it completely or only uninstall it but keep track of new versions?", "Delete Mod", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, new String[]{"Delete completely", "Uninstall but keep in list to check versions", "Cancel"}, null);
				}
				if (reply == JOptionPane.YES_OPTION || (mod.isInstallable() && reply == JOptionPane.NO_OPTION)) {
					uninstallMod(mod);
					if (reply == JOptionPane.YES_OPTION) {
						removeMod(mod);
					} else {
						mod.setInstallable(false);
						listUpdate(false, mainList.getSelectedRow());
					}
					saveConfigFile();
				}
			}
		}
	}
	
	public void actionPerformed(ActionEvent e) {
		if(e.getSource()==downloadBut) {
			getAddon();
		} else if(e.getSource()==installBut) {
			synchronized(lock) {
				nextInstall();
			}
		} else if(e.getSource()==mmButton) {
			if (moduleManagerMod == null) {
				moduleManagerMod = new Mod("Module Manager dll", config.moduleManagerLink, true);
			}
			if (moduleManagerMod.isValid == false) {
				alertBox(null, "Error adding Module Manager Mod.");
			} else {
				getAddon(moduleManagerMod.getName(), moduleManagerMod.getLink());
			}
		} else if(e.getSource()==updateBut) {
			synchronized(lock) {
				updateMods();
			}
		} else if(e.getSource()==configBut) {
			openConfigWindow();
		}
	}
	
	void uninstallMod(Mod mod) {
		uninstallMod(mod, true);
	}
	
	void uninstallMod(Mod mod, boolean alerts) {
		if (mod.isInstallable() == false) {
			return;
		}
		List<Path> updatedFiles = new ArrayList<Path>();
		List<Path> parents = new ArrayList<Path>();
		List<Path> parentsNotRemoved = new ArrayList<Path>();
		
		Path mainData = Paths.get(config.kspDataFolder);
		
		for (ModFile f: mod.getInstalledFiles()) {
			Path file = f.getPath();
			if (f.isUpdated() == true) {
				updatedFiles.add(file);
			} else {
				try {
					Files.deleteIfExists(file);
				} catch (Exception e) {
					
				}
				boolean found = false;
				for (Path ff: parents) {
					try {
						if (Files.isSameFile(ff, file.getParent())) {
							found = true;
							break;
						}
					} catch (Exception e) {
						
					}
				}
				if (found == false) {
					parents.add(file.getParent());
				}
			}
		}
		mod.clearInstalledFiles();
		for (Path f: parents) {
			boolean sameFile = false;
			try {
				while (f != null && !Files.isSameFile(mainData, f)) {
					String[] flist = f.toFile().list();
					if (flist == null || flist.length == 0) {
						try {
							Files.deleteIfExists(f);
						}  catch (Exception ee) {
							
						}
						f = f.getParent();
					} else {
						boolean found = false;
						for (Path ff: parentsNotRemoved) {
							try {
								if (Files.isSameFile(ff, f)) {
									found = true;
									break;
								}
							} catch (Exception ee) {
								
							}
						}
						if (found == false) {
							parentsNotRemoved.add(f);
						}
						break;
					}
				}
			}  catch (Exception e) {
			}
		}
		boolean notRemoved = false;
		for (int i = parentsNotRemoved.size()-1; i >= 0; i--) {
			if (Files.exists(parentsNotRemoved.get(i)) == true) {
				notRemoved = true;
			} else {
				parentsNotRemoved.remove(i);
			}
		}
		if (alerts == true) {
			if (notRemoved == true) {
				try {
					FileWriter f0 = new FileWriter("log.txt", true);
					String newLine = System.getProperty("line.separator");
					Date d = new Date();
					f0.write(newLine + newLine + "********* " + getCurrentTimeStamp() + " *********" + newLine + " - Error removing mod " + mod.getName() + " because these directories were not empty:" + newLine);
					for(int i=0;i<parentsNotRemoved.size();i++) {
						try {
							f0.write(parentsNotRemoved.get(i).toFile().getCanonicalPath() + newLine);
						} catch (Exception e) {
							
						}
					}
					f0.close();
				} catch (Exception e) {
					
				}
				alertBox(null, "Could not remove some directories because they were not empty. Maybe the mod is merged with another mod or simply the mod created a config file on the fly.\nCheck those directories manually AFTER closing this app. You have the list in the log.txt file.");
			}
			if (updatedFiles.size() > 0) {
				try {
					FileWriter f0 = new FileWriter("log.txt", true);
					String newLine = System.getProperty("line.separator");
					Date d = new Date();
					f0.write(newLine + newLine + "********* " + getCurrentTimeStamp() + " *********" + newLine + " - Error removing mod " + mod.getName() + " because these files overwrited some other files:" + newLine);
					for(int i=0;i<updatedFiles.size();i++) {
						try {
							f0.write(updatedFiles.get(i).toFile().getCanonicalPath() + newLine);
						} catch (Exception e) {
							
						}
					}
					f0.close();
				} catch (Exception e) {
					
				}
				alertBox(null, "Some files could not be removed because they replaced other mod files, you must check and decide if you remove them manually AFTER closing this app.\nYou have the list in the log.txt file.");
			}
		}
	}
	
	String getCurrentTimeStamp() {
		SimpleDateFormat sdfDate = new SimpleDateFormat();
		Date now = new Date();
		String strDate = sdfDate.format(now);
		return strDate;
	}
	
	boolean removeMod(Mod mod) {
		String modFileName = "data" + File.separator + mod.getUniqueId().toString();
		DirIO.clearDir(modFileName);
		for (int i = 0; i < modList.size(); i++) {
			if (modList.get(i).getUniqueId() == mod.getUniqueId()) {
				modList.remove(i);
				listUpdate(true, -1);
				return true;
			}
		}
		return false;
	}
	
	public class MyAsyncModDownload implements Runnable {
		private Mod mod;

		MyAsyncModDownload(Mod mod) {
			this.mod = mod;
		}

		@Override
		public void run() {
			try {
				this.mod.setStatus(" - [Downloading - 0%] -");
				setMod(this.mod);
				if (downloadMod(this.mod)) {
					this.mod.setStatus(" - [Install Queue] -");
					setMod(this.mod);
					synchronized(lock) {
						modInstallQeue.add(this.mod);
					}
				} else {
					removeMod(this.mod);
				}
				synchronized(lock) {
					asyncDThread = null;
					installBut.setEnabled(true);
					nextDownload();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	boolean downloadMod(Mod mod) {
		if (mod.isInstallable() == false) {
			return true;
		}
		InputStream in = null;
		FileOutputStream fout = null;
		try {
			synchronized(lock) {
				File f = new File("temp");
				if (!f.exists()) {
					f.mkdirs();
				} else if (!f.isDirectory()) {
					alertBox(null, mod.getName() + ": Error accesing temp folder.");
					return false;
				}
			}
			
			String filename = "";
			int fsize = 0;
			
			if (mod.getDownloadLink().equals("")) {
				String link = ModDataParser.getDownloadLink(mod);
				if (link.equals("")) {
					alertBox(null, mod.getName() + ": Error getting download link.");
					return false;
				} else {
					mod.setDownloadLink(link);
				}
			}
			
			if (Http.fileType(mod.getDownloadLink()) != Http.ZIP_EXTENSION) {
				String dlink = Http.getDownloadLink(mod.getDownloadLink());
				if (dlink == null) {
					Browser browser = new Browser();
					browser.show(mod.getDownloadLink(), mod);
					if (!browser.downloadFile.equals("")) {
						dlink = browser.downloadFile;
					}
					if (browser.modReloaded == true) {
						return downloadMod(mod);
					}
				}
				if (dlink != null && Http.fileType(dlink) ==  Http.ZIP_EXTENSION) {
					mod.setDownloadLink(dlink);
				} else {
					alertBox(null, mod.getName() + ": Error getting download link.");
					return false;
				}
			}
			
			HttpURLConnection conn = Http.getConnection(mod.getDownloadLink());
			mod.setDownloadLink("");
			if (conn == null) {
				alertBox(null, mod.getName() + ": Error getting download link.");
				return false;
			}
			
			String header = conn.getHeaderField("Content-Disposition");
			if(header != null && header.indexOf("=") != -1) {
				filename = header.split("=")[1];
			} else {
				filename = "default.zip";
			}
			fsize = conn.getContentLength();
			
			filename = filename.replace("\\", "_");
			filename = filename.replace("/", "_");
			filename = filename.replace("\"", "");
			
			in = conn.getInputStream();
			fout = new FileOutputStream("temp" + File.separator + filename);
			
			final byte data[] = new byte[512];
			int count = 0;
			int total = 0;
			int lastPerc = 0;
			while (closingApp == false && (count = in.read(data, 0, 512)) != -1) {
				total = total + count;
				int perc = (int)((total*100.0f)/fsize);
				if (lastPerc != perc) {
					lastPerc = perc;
					mod.setStatus(" - [Downloading - "+lastPerc+"%] -");
					setMod(mod);
				}
				fout.write(data, 0, count);
			}
			mod.downloadedFile = "temp" + File.separator + filename;
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			try {
				if (fout != null) {
					fout.close();
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		return true;
	}
	
	public class MyAsyncModInstall implements Runnable {
	
		private Mod mod;
		private int forceCopy = -1;
		
		MyAsyncModInstall(Mod mod) {
			this.mod = mod;
		}
		
		List<ModFile> copyFiles(String mainPath, String copyPath) {
			List<ModFile> copied = new ArrayList<ModFile>();
			
			Path target = Paths.get(config.kspDataFolder);
			
			Path dir = Paths.get(mainPath, copyPath);
			Path mainDir = Paths.get(mainPath);
			
			try {
				Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						Path relativePath = mainDir.relativize(file);
						Path dest = target.resolve(relativePath);
						File destFile = dest.toFile();
						ModFile f = new ModFile(dest, false);
						
						boolean copy = true;
						if (destFile.exists()) {
							copy = false;
							f.setUpdated(true);
							if (forceCopy == -1) {
								String relPath = relativePath.toFile().getPath();
								JCheckBox c = new JCheckBox("Remember this selection");
								final JComponent[] inputs = new JComponent[] {
									new JLabel("Do you want to overwrite this file?"),
									new JLabel("GameData" + File.separator + relPath),
									c
								};
								int reply = JOptionPane.showConfirmDialog(null, inputs, "Add new mod", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
								if (reply == JOptionPane.YES_OPTION) {
									copy = true;
								}
								if (c.isSelected()) {
									forceCopy = reply==JOptionPane.YES_OPTION?1:0;
								}
							} else if (forceCopy == 1) {
								copy = true;
							}
						}
						if (copy == true) {
							destFile.mkdirs();
							Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
							copied.add(f);
						}
						return CONTINUE;
					}
				});
			} catch (Exception e) {
			}
			return copied;
		}
		
		JPanel getPanel(List<String> gameDatas, List<String> gameTxt) {
			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
			JLabel titleTxt = new JLabel(this.mod.getName());
			Font font = titleTxt.getFont().deriveFont(titleTxt.getFont().getSize2D()+5.0f);
			titleTxt.setFont(font);
			titleTxt.setAlignmentX(Component.CENTER_ALIGNMENT);
			panel.add(titleTxt);
			
			if (gameTxt.size() > 0) {
				JButton readmeBut = new JButton("Open README file (May have install instructions)");
				readmeBut.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						if (gameTxt.size() == 1) {
							File file = new File(gameTxt.get(0));
							try {
								Desktop.getDesktop().edit(file);
							} catch(Exception ex) {
								ex.printStackTrace();
							}
						} else {
							JPanel readmePanel = new JPanel();
							readmePanel.setLayout(new BoxLayout(readmePanel, BoxLayout.PAGE_AXIS));
							for (String txt: gameTxt) {
								File file = new File(txt);
								JButton but = new JButton(file.getName());
								but.setAlignmentX(Component.CENTER_ALIGNMENT);
								but.addActionListener(new ActionListener() {
									@Override
									public void actionPerformed(ActionEvent e) {
										try {
											Desktop.getDesktop().edit(file);
										} catch(Exception ex) {
											ex.printStackTrace();
										}
									}
								});
								readmePanel.add(but);
							}
							JOptionPane.showMessageDialog(null, readmePanel, "Readme files", JOptionPane.PLAIN_MESSAGE);
						}
					}
				});
				readmeBut.setAlignmentX(Component.CENTER_ALIGNMENT);
				panel.add(readmeBut);
			}
			JLabel installTxt = new JLabel("Mark items to install");
			installTxt.setAlignmentX(Component.CENTER_ALIGNMENT);
			panel.add(installTxt);
			
			int i = 0;
			JPanel gameDatasPanel = new JPanel();
			gameDatasPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
			for (String gdata: gameDatas) {
				i++;
				File gdataFile = new File(gdata);
				String gdataTxt = gdataFile.getParentFile().getName();
				if (gdataTxt.equals("temp")) {
					gdataTxt = "Main GameData";
				}
				JPanel gameDataPanel = new JPanel();
				gameDataPanel.setLayout(new BoxLayout(gameDataPanel, BoxLayout.PAGE_AXIS));
				
				String[] undeededFilesArray = new String[]{"(source)", "(sources)", "(.*)(.txt)", "(.*)(.asciidoc)", "(.*)(.md)"};
				String[] mmArray = new String[]{"(modulemanager)(.*)(\\.dll)"};
				int arraysize = 0;
				if (config.excludeUnneededFiles == true) {
					arraysize += undeededFilesArray.length;
				}
				if (config.excludeModuleManagerDll == true && !(moduleManagerMod != null && this.mod.getId().equals(moduleManagerMod.getId()))) {
					arraysize += mmArray.length;
				}
				String[] excludeList = new String[arraysize];
				if (config.excludeUnneededFiles == true) {
					System.arraycopy(undeededFilesArray, 0, excludeList, 0, undeededFilesArray.length);
				}
				if (config.excludeModuleManagerDll == true && !(moduleManagerMod != null && this.mod.getId().equals(moduleManagerMod.getId()))) {
					System.arraycopy(mmArray, 0, excludeList, (config.excludeUnneededFiles?undeededFilesArray.length:0), mmArray.length);
				}
				
				FileTreeModel f = new FileTreeModel(gdataFile, excludeList);
				f.setAlignmentX(Component.CENTER_ALIGNMENT);
				TitledBorder title = BorderFactory.createTitledBorder(gdataTxt);
				f.setBorder(BorderFactory.createTitledBorder(gdataTxt));
				gameDataPanel.add(f);
				
				JButton but = new JButton("Install this GameData");
				but.setAlignmentX(Component.CENTER_ALIGNMENT);
				Mod mod = this.mod;
				but.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						but.setText("Install again");
						TreePath[] files = f.checkTreeManager.getSelectionModel().getSelectionPaths();
						List<ModFile> installedFiles = new ArrayList<ModFile>();
						if (files != null) {
							forceCopy = -1;
							for	(int ii = 0; ii < files.length; ii++) {
								Object paths[] = files[ii].getPath();
								String path = "";
								for (int iii = 1; iii < paths.length; iii++) {
									path = path + File.separator + (String)((DefaultMutableTreeNode)paths[iii]).getUserObject();
								}
								List <ModFile> copiedfiles = copyFiles(gdata, path);
								for (ModFile f: copiedfiles) {
									installedFiles.add(f);
								}
							}
						}
						for (ModFile f: installedFiles) {
							mod.addInstalledFile(f);
						}
						alertBox(null, "Installed " + installedFiles.size() + " files.");
					}
				});
				gameDataPanel.add(but);
				
				gameDatasPanel.add(gameDataPanel);
			}
			panel.add(gameDatasPanel);
			
			return panel;
		}
		
		@Override
		public void run() {
			this.mod.setStatus(" - [Extracting...] -");
			setMod(this.mod);
			List<String> gameDatas = new ArrayList<String>();
			List<String> gameTxt = new ArrayList<String>();
			int modType = Zip.getModInfo(this.mod.downloadedFile, gameDatas, gameTxt);
			String modExtract = "temp" + File.separator + "GameData";
			if (gameDatas.size() == 0) {
				gameDatas.add(modExtract);
				if (Zip.test(modType, Zip.NO_MAINFOLDER)) {
					String modMainDir = replaceCharacters(this.mod.getName()) + "_" + this.mod.getUniqueId().toString();
					modExtract = modExtract + File.separator + modMainDir;
				}
			} else {
				for (int i = 0; i < gameDatas.size(); i++) {
					gameDatas.set(i, modExtract + File.separator + gameDatas.get(i));
				}
			}
			for (int i = 0; i < gameTxt.size(); i++) {
				gameTxt.set(i, modExtract + File.separator + gameTxt.get(i));
			}
			
			try {
				Zip.extract(this.mod.downloadedFile, modExtract);
			} catch (Exception e) {
				e.printStackTrace();
			}
			int i = 0;
			this.mod.setStatus(" - [Installing...] -");
			setMod(this.mod);
			alertBox(null, this.mod.getName() + ":\nFound " + gameDatas.size() + " GameData folders" + (gameTxt.size()>0?(" and " + gameTxt.size() + " README files."):(".")));
			
			JOptionPane.showMessageDialog(null, getPanel(gameDatas, gameTxt), "Install", JOptionPane.PLAIN_MESSAGE);
			
			this.mod.setStatus("");
			setMod(this.mod);
			saveConfigFile();
			DirIO.clearDir("temp" + File.separator + "GameData");
			DirIO.clearDir(this.mod.downloadedFile);
			synchronized(lock) {
				asyncDThread = null;
				downloadBut.setEnabled(true);
				nextInstall();
			}
		}
	}
	
	public class MyAsyncModUpdate implements Runnable {
		List<Mod> updateList;
		public List<Mod> updateList2;
		boolean force;
		
		MyAsyncModUpdate(List<Mod> updateList, boolean force) {
			this.updateList = new ArrayList(updateList);
			this.force = force;
		}
		
		@Override
		public void run() {
			List<Mod> noInstallList = new ArrayList();
			int updated = 0;
			int updatedInstall = 0;
			for (Mod mod: this.updateList) {
				if (closingApp == false && mod.getStatus().equals("")) {
					mod.setStatus(" - [Checking...] -");
					mod.justUpdated = false;
					setMod(mod);
					Mod newMod = new Mod(mod.getName(), mod.getLink(), mod.isInstallable());
					newMod.setUniqueId(mod.getUniqueId());
					if (force == true || !mod.getVersion().equals(newMod.getVersion())) {
						if (!mod.getVersion().equals(newMod.getVersion())) {
							newMod.justUpdated = true;
						}
						updated++;
						if (newMod.isInstallable()) {
							uninstallMod(mod, false);
							removeMod(mod);
							updatedInstall++;
							newMod.setStatus(" - [Downloading - 0%] -");
							setMod(newMod);
							if (downloadMod(newMod)) {
								newMod.setStatus(" - [Install Queue] -");
								setMod(newMod);
								synchronized(lock) {
									modInstallQeue.add(newMod);
								}
							} else {
								removeMod(newMod);
							}
						} else {
							setMod(newMod);
						}
					} else {
						mod.setStatus("");
						setMod(mod);
					}
				}
			}
			saveConfigFile();
			if (closingApp == false) {
				if (updated > 0 && force == false) {
					alertBox(null, "Found " + updated + " mods updated. " + (updatedInstall==updated?"All":(updatedInstall>0?updatedInstall:"None")) + " of them were added to the Install Queue.");
				}
				/*if (noInstallList.size() > 0) {
					JPanel panel = new JPanel();
					panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
					JLabel titleLabel = new JLabel(noInstallList.size() + " mod " + (noInstallList.size()==1?"is":"are") + " marked to not install:");
					titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
					panel.add(titleLabel);
					for (Mod m: noInstallList) {
						JButton b = new JButton(m.getName());
						b.setAlignmentX(Component.CENTER_ALIGNMENT);
						b.addActionListener(new ActionListener() {
							@Override
							public void actionPerformed(ActionEvent e) {
								try {
									Desktop.getDesktop().browse(new URI(m.getLink()));
								} catch (Exception ee) {
								}
							}
						});
						panel.add(b);
					}
					JLabel footerLabel = new JLabel("Click the buttons to open website.");
					footerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
					panel.add(footerLabel);
					JOptionPane.showMessageDialog(null, panel, "Readme files", JOptionPane.PLAIN_MESSAGE);
				}*/
				synchronized(lock) {
					asyncDThread = null;
					if (modInstallQeue.size() > 0) {
						installBut.setEnabled(true);
					}
					nextDownload();
				}
			}
		}
	}
	
	String replaceCharacters(String str) {
		return str.replaceAll("[\\W]|_", "");
	}
	
	void nextDownload() {
		if (closingApp == false && (asyncDThread == null || !asyncDThread.isAlive())) {
			if (modQeue.size() > 0) {
				installBut.setEnabled(false);
				updateBut.setEnabled(false);
				Runnable asyncDRunnable = new MyAsyncModDownload(modQeue.remove(0));
				asyncDThread = new Thread(asyncDRunnable);
				asyncDThread.start();
			} else {
				updateBut.setEnabled(true);
			}
		}
	}
	
	void nextInstall() {
		if (closingApp == false && (asyncDThread == null || !asyncDThread.isAlive())) {
			if (modInstallQeue.size() > 0) {
				downloadBut.setEnabled(false);
				installBut.setEnabled(false);
				updateBut.setEnabled(false);
				Runnable asyncDRunnable = new MyAsyncModInstall(modInstallQeue.remove(0));
				asyncDThread = new Thread(asyncDRunnable);
				asyncDThread.start();
			} else {
				updateBut.setEnabled(true);
				installBut.setEnabled(false);
			}
		}
	}
	
	void updateMods() {
		updateMods(modList, false);
	}
	
	void updateMods(List<Mod> list) {
		updateMods(list, false);
	}
	
	void updateMods(List<Mod> list, boolean force) {
		if (closingApp == false && (asyncDThread == null || !asyncDThread.isAlive())) {
			installBut.setEnabled(false);
			Runnable asyncDRunnable = new MyAsyncModUpdate(list, force);
			asyncDThread = new Thread(asyncDRunnable);
			asyncDThread.start();
		}
	}
	
	void getAddon() {
		getAddon("", "");
	}
	
	void getAddon(String name, String urlText) {
		JTextField modName = new JTextField();
		JTextField modUrl = new JTextField();
		try {
			String cbData = (String)Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
			if (cbData.startsWith("http://") || cbData.startsWith("https://")) {
				modUrl.setText(cbData);
			}
		} catch( Exception e) {
		}
		JCheckBox check = new JCheckBox("Do not install, only warn me when there's a new version.");
		int reply = JOptionPane.OK_OPTION;
		
		while((urlText.length() == 0 || name.length() == 0) && reply == JOptionPane.OK_OPTION) {
			final JComponent[] inputs = new JComponent[] {
				new JLabel("Name this mod"),
				modName,
				new JLabel("URL"),
				modUrl,
				check
			};
			modName.addAncestorListener( new RequestFocusListener() );
			reply = JOptionPane.showConfirmDialog(null, inputs, "Add new mod", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
			if (reply == JOptionPane.OK_OPTION) {
				urlText = modUrl.getText();
				name = modName.getText();
			}
		}
		
		if (reply != JOptionPane.OK_OPTION) {
			return;
		}
		
		synchronized(lock) {
			Mod mod = new Mod(name, urlText, !check.isSelected());
			for (Mod m: modList) {
				if (m.getId().equals(mod.getId())) {
					alertBox(null, "That mod already exists in the mod list under name: " + m.getName());
					return;
				}
			}
			
			if (mod.isInstallable()) {
				mod.setStatus(" - [Download Queue] -");
			}
			if (mod.isValid == false) {
				alertBox(null, "Error getting mod " + mod.getName() + ". Check the URL.");
			} else {
				setMod(mod);
				if (mod.isInstallable() == false) {
					saveConfigFile();
				} else {
					modQeue.add(mod);
				}
				nextDownload();
			}
		}
	}
	
	void setMod(Mod mod) {
		boolean found = false;
		int tabley = 0;
		synchronized(lock) {
			for (tabley = 0; tabley < modList.size(); tabley++) {
				if (modList.get(tabley).getUniqueId() == mod.getUniqueId()) {
					modList.set(tabley, mod);
					found = true;
					break;
				}
			}
			if (found == true) {
				if (mod.nameChanged) {
					listUpdate(false, -1);
				} else {
					listUpdate(false, tabley);
				}
			} else {
				modList.add(mod);
				listUpdate(true, -1);
			}
			mod.nameChanged = false;
		}
	}
	
	void listUpdate() {
		listUpdate(true, -1);
	}
	
	void listUpdate(boolean rowQuantityChange) {
		listUpdate(rowQuantityChange, -1);
	}
	
	void listUpdate(boolean rowQuantityChange, int row) {
		synchronized(lock) {
			if (rowQuantityChange) {
				Collections.sort(modList, new myComparator());
				((AbstractTableModel)mainList.getModel()).fireTableStructureChanged();
			} else if (row > -1) {
				((AbstractTableModel)mainList.getModel()).fireTableRowsUpdated(row, row);
			} else {
				Collections.sort(modList, new myComparator());
				((AbstractTableModel)mainList.getModel()).fireTableDataChanged();
			}
		}
	}
	
	void alertBox(Component title, String txt) {
		JOptionPane.showMessageDialog(title, txt);
	}
	
	void saveConfigFile() {
		try {
			File f = new File("data" + File.separator + "config.xml");
			if (!f.getParentFile().exists()) {
				f.getParentFile().mkdirs();
			}
			
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

			xmlDoc = docBuilder.newDocument();
			rootElement = xmlDoc.createElement("data");
			xmlDoc.appendChild(rootElement);

			synchronized(lock) {
				for(Mod mlist: modList) {
					if (mlist.getStatus().equals("")) {
						try {
							String modFileName = "data" + File.separator + mlist.getUniqueId().toString() + File.separator + "Mod.object";
							File modFile = new File(modFileName);
							if (!modFile.getParentFile().exists()) {
								modFile.getParentFile().mkdirs();
							}
							ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(modFile));
							out.writeObject(mlist);
							out.close();
							Element modFileNameElement = xmlDoc.createElement("modFileName");
							modFileNameElement.setAttribute("file", modFileName);
							rootElement.appendChild(modFileNameElement);
						} catch (Exception e) {
						}
					}
				}
			}
			
			Element configElement = xmlDoc.createElement("config");
			rootElement.appendChild(configElement);
			
			Element configVersionElement = xmlDoc.createElement("configVersion");
			configVersionElement.appendChild(xmlDoc.createTextNode("1"));
			configElement.appendChild(configVersionElement);
			
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(xmlDoc);
			StreamResult result = new StreamResult(f);
			transformer.transform(source, result);
			
			File managerConfigFile = new File("data" + File.separator + "ManagerConfig.object");
			if (!managerConfigFile.getParentFile().exists()) {
				managerConfigFile.getParentFile().mkdirs();
			}
			ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(managerConfigFile));
			out.writeObject(config);
			out.close();
			
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	void loadConfigFile() {
		try {
			File stocks = new File("data/config.xml");
			if (stocks.exists()) {
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
				Document doc = dBuilder.parse(stocks);
				doc.getDocumentElement().normalize();
				NodeList nodes = doc.getElementsByTagName("modFileName");
				for (int i = 0; i < nodes.getLength(); i++) {
					Node node = nodes.item(i);
					
					if (node.getNodeType() == Node.ELEMENT_NODE) {
						try {
							Element element = (Element)node;
							String modFileName = element.getAttribute("file");
							
							FileInputStream f_in = new FileInputStream(modFileName);
							
							ObjectInputStream obj_in = new ObjectInputStream (f_in);
							
							Mod mod = (Mod)obj_in.readObject();
							setMod(mod);
						} catch (Exception e) {
						}
					}
				}
				
				try {
					FileInputStream f_in = new FileInputStream("data" + File.separator + "ManagerConfig.object");
					ObjectInputStream obj_in = new ObjectInputStream (f_in);
					config = (ManagerConfig)obj_in.readObject();
				} catch (Exception ex) {
				}
				
				nodes = doc.getElementsByTagName("config");
				if (nodes.getLength() > 0) {
					Node node = nodes.item(0);
					
					if (node.getNodeType() == Node.ELEMENT_NODE) {
						Element element = (Element) node;
						
						String configVersion = getNodeValue("configVersion", element);
						if (configVersion.equals("1")) {
							// Config is OK. This is for future config version changes.
						}
					}
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		File f = new File(config.kspDataFolder);
		if (config.kspDataFolder.equals("") || !f.exists() || !f.isDirectory()) {
			if (config.selectKspFolder(this) == false) {
				System.exit(0);
			}
		}
	}
	
	void openConfigWindow() {
		config.change(this);
		saveConfigFile();
	}
	
	private static String getNodeValue(String tag, Element element) {
		NodeList nodes = element.getElementsByTagName(tag).item(0).getChildNodes();
		Node node = (Node) nodes.item(0);
		return node.getNodeValue();
	}
	
	class MyPopMenu extends JPopupMenu implements ActionListener {
		JMenuItem menuItemRename = new JMenuItem("Rename", new ImageIcon(getClass().getResource("/images/rename.gif")));
		JMenuItem menuItemOpenLink = new JMenuItem("Open mod link in browser", new ImageIcon(getClass().getResource("/images/link.gif")));
		JMenuItem menuItemReinstall = new JMenuItem("Redownload", new ImageIcon(getClass().getResource("/images/install.png")));
		JMenuItem menuItemUpdate = new JMenuItem("Check update", new ImageIcon(getClass().getResource("/images/update.png")));
		JMenuItem menuItemDelete = new JMenuItem("Uninstall", new ImageIcon(getClass().getResource("/images/delete.png")));
		
		JMenuItem menuItemAllDisabled = new JMenuItem("Disabled because of Download/Install operations");
		
		Mod mod;
		
		public MyPopMenu(Mod mod) {
			this.mod = mod;
			if (this.mod.isInstallable() == false) {
				menuItemReinstall.setText("Download");
				menuItemDelete.setText("Remove");
			}
			
			if (asyncDThread != null) {
				menuItemRename.setEnabled(false);
				menuItemReinstall.setEnabled(false);
				menuItemUpdate.setEnabled(false);
				menuItemDelete.setEnabled(false);
				
				this.add(menuItemAllDisabled);
				this.addSeparator();
			}
			
			this.add(menuItemRename);
			this.add(menuItemOpenLink);
			this.addSeparator();
			this.add(menuItemReinstall);
			this.add(menuItemUpdate);
			this.addSeparator();
			this.add(menuItemDelete);
			
			menuItemRename.addActionListener(this);
			menuItemOpenLink.addActionListener(this);
			menuItemReinstall.addActionListener(this);
			menuItemUpdate.addActionListener(this);
			menuItemDelete.addActionListener(this);
			
		}
		
		public void actionPerformed(ActionEvent e) {
			if(e.getSource()==menuItemRename) {
				renameSelectedMod();
			} else if(e.getSource()==menuItemOpenLink) {
				try {
					Desktop.getDesktop().browse(new URI(this.mod.getLink()));
				} catch (Exception ee) {
				}
			} else if(e.getSource()==menuItemReinstall) {
				reinstallSelectedMod();
			} else if(e.getSource()==menuItemUpdate) {
				updateSelectedMod();
			} else if(e.getSource()==menuItemDelete) {
				removeSelectedMod();
			}
		}
	}
	
	public static void main(String[] ar) {
		CookieHandler.setDefault( new CookieManager( null, CookiePolicy.ACCEPT_ALL ) );
		if ((new File("temp")).exists()) {
			DirIO.clearDir("temp");
		}
		Main window=new Main();
		window.setSize(500,500);
		window.setResizable(false);
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.setVisible(true);

		window.setLocationRelativeTo(null);
		
		window.loadConfigFile();
	}
}

class Zip {
	public static final int NO_MAINFOLDER = 0b00000001;
	
	public static boolean test(int flags, int mask) { return ((flags & mask) == mask); }
	public static int set(int flags, int mask) { return (flags |= mask); }
	public static int clear(int flags, int mask) { return(flags &= ~mask); }
	
	public static void extract(String zipFile, String outputFolder) {
		byte[] buffer = new byte[1024];
		try {
			File folder = new File(outputFolder);
			if(!folder.exists()) {
				folder.mkdir();
			}
			ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
			ZipEntry ze;
			while((ze = zis.getNextEntry()) != null) {
				String fileName = ze.getName();
				File newFile = new File(outputFolder + File.separator + fileName);
				
				new File(newFile.getParent()).mkdirs();
				
				if (ze.isDirectory()) {
					if(!newFile.exists()) {
						newFile.mkdir();
					}
				} else {
					FileOutputStream fos = new FileOutputStream(newFile);
					
					int len;
					while ((len = zis.read(buffer, 0, 1024)) > 0) {
						fos.write(buffer, 0, len);
					}
					
					fos.close();
				}
			}
			
			zis.closeEntry();
			zis.close();
		} catch(Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public static int getModInfo(String zipFile, List<String> gameDatas, List<String> readmes) {
		int type = 0;
		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
			ZipEntry ze;
			while((ze = zis.getNextEntry()) != null) {
				String filePath = ze.getName();
				File file = new File(filePath);
				
				if (gameDatas != null) {
					File pFile = file;
					String parentData = "";
					while ((pFile = pFile.getParentFile()) != null) {
						if (pFile.getName().equals("GameData")) {
							parentData = pFile.getPath();
						}
					}
					if (!parentData.equals("")) {
						boolean found = false;
						for (String d: gameDatas) {
							if (d.equals(parentData)) {
								found = true;
								break;
							}
						}
						if (found == false) {
							gameDatas.add(parentData);
						}
					}
				}
				
				String fileName = file.getName();
				if (!test(type, NO_MAINFOLDER) && file.getParent() == null) {
					String[] mainDirs = {"Parts", "Plugins"};
					for (String dir: mainDirs) {
						if (fileName.equals(dir)) {
							type = set(type, NO_MAINFOLDER);
							break;
						}
					}
				}
				
				if (!ze.isDirectory() && readmes != null) {
					int i = fileName.lastIndexOf('.');
					if (i > 0) {
						String[] readableExtensions = {"txt", "asciidoc", "md"};
						for (String ext: readableExtensions) {
							if (ext.equals(fileName.substring(i+1).toLowerCase())) {
								readmes.add(filePath);
								break;
							}
						}
					}
				}
			}
			if (test(type, NO_MAINFOLDER) && gameDatas != null && gameDatas.size() > 0) {
				type = clear(type, NO_MAINFOLDER);
			}
			zis.closeEntry();
			zis.close();
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		return type;
	}
}

class DirIO {
	public static void clearDir(String folder) {
		boolean copied;
		long t = System.currentTimeMillis();
		do {
			copied = cDir(folder);
		} while(copied == false && System.currentTimeMillis()-t < 5000);
	}

	private static boolean cDir(String folder) {
		Path dir = Paths.get(folder);
		if (Files.exists(dir)) {
			try {
				Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						Files.delete(file);
						return CONTINUE;
					}
					
					@Override
					public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
						if (exc == null) {
							Files.delete(dir);
							return CONTINUE;
						} else {
							throw exc;
						}
					}
		 
				});
			} catch (Exception e) {
				return false;
			}
		}
		return true;
	}
}

class myComparator implements Comparator<Mod> {
	@Override
	public int compare(Mod a, Mod b) {
		return a.getName().compareToIgnoreCase(b.getName());
	}
}