/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pcd.gui;

import java.awt.Font;
import java.awt.KeyboardFocusManager;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import pcd.imageviewer.ImageMouseMotionListener;
import pcd.imageviewer.ImageViewer;
import pcd.imageviewer.ResizeStrategy;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.swing.CellEditor;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import org.apache.commons.lang3.Range;
import pcd.data.ImageDataObject;
import pcd.data.ImageDataStorage;
import pcd.data.PcdPoint;
import pcd.gui.base.FileChooserAccesory.FileSearchAccessory;
import pcd.gui.base.ImgFileFilter;
import pcd.gui.base.PCDClickListener;
import pcd.gui.base.PCDMoveListener;
import pcd.gui.base.ProjectFileFilter;
import pcd.gui.dialog.FileListPopup;
import pcd.gui.dialog.InteractiveModeDialog;
import pcd.utils.Constant;
import pcd.utils.FileUtils;

/**
 *
 * @author ixenr
 */
public final class MainFrame extends javax.swing.JFrame {

    private final ImageDataStorage imgDataStorage;
    private final ImageViewer imagePane;
    private boolean hasOverlay = false;
    private int current_selected = -1;
    private final JComponent imagePaneComponent;
    private final JScrollPane imageScrollComponent;
    private final PCDClickListener mouseListenerClick;
    private FileSearchAccessory fileSearchAccessory;
    private final DefaultTableModel fileListTableModel;
    private final ImgFileFilter filter = new ImgFileFilter();
    private final ProjectFileFilter pcdfilter = new ProjectFileFilter();

    private boolean listenerAdded = false;
    private boolean listenerActive = false;

    private Path savePath = null;
    private Path lastChoosePath = null;

    private double DEFAULT_ZOOM = 0.223;
    private double ZOOM_DIFF = (1.0 - DEFAULT_ZOOM) / 3;

    private final static int IMG_WIDTH = 3406;
    private final static int IMG_HEIGHT = 2672;

    public MainFrame(ImageDataStorage imgDataStorage) {

        this.imgDataStorage = imgDataStorage;
        imgDataStorage.setFrame(this);
        //imgProc.addImage("1.png");
        imagePane = new ImageViewer(null, false);
        imagePaneComponent = imagePane.getComponent();
        imagePane.setResizeStrategy(ResizeStrategy.RESIZE_TO_FIT);
        imagePane.setZoomFactor(DEFAULT_ZOOM);
        //imagePane.setImage(imgProc.getImageObject(0));
        mouseListenerClick = new PCDClickListener(this, imgDataStorage);
        ImageMouseMotionListener mouseListenerMotion = new PCDMoveListener(this, imgDataStorage);
        imagePane.addImageMouseClickListener(mouseListenerClick);
        imagePane.addImageMouseMotionListener(mouseListenerMotion);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            ImageDataStorage.getLOGGER().error("Unable to retrieve GUI instance or UI style", e);
        }

        initComponents();
        this.fileListTableModel = (DefaultTableModel) fileListTable.getModel();
        opacitySlider.setEnabled(false);

        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent componentEvent) {
                DEFAULT_ZOOM = (double) imagePanel.getHeight() / IMG_HEIGHT;
                ZOOM_DIFF = (1. - DEFAULT_ZOOM) / 3;
                imagePane.setZoomFactor(DEFAULT_ZOOM);
            }
        });

        imageScrollComponent = imagePane.getScrollPane();
        imageScrollComponent.setWheelScrollingEnabled(false);
        imageScrollComponent.addMouseWheelListener(this::mouseWheelMoved);

        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher((KeyEvent e) -> {
                    if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_STANDARD && 58 >= e.getKeyCode() && e.getKeyCode() >= 49) {
                        int val = e.getKeyCode() - 49;
                            if (val >= pointAddTypeSelect.getItemCount()) {
                                val = pointAddTypeSelect.getItemCount() - 1;
                            }
                        
                        if (e.isShiftDown()) {
                            PcdPoint p = mouseListenerClick.getSelection();
                            if(p == null || imgDataStorage.getCurrent().getOverlay() == null)
                                return true;
                            imgDataStorage.setPointType(p, (String) pointAddTypeSelect.getItemAt(val));
                            imgDataStorage.getCurrent().getOverlay().repaint();
                            loadTables();
                        } else {
                            pointAddTypeSelect.setSelectedIndex(val);
                            return true;
                        }
                    }

                    return false;
                });

        this.setDropTarget(new DropTarget() {
            @Override
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    ImgFileFilter f = new ImgFileFilter();
                    droppedFiles.stream().filter(file -> (f.accept(file))).forEachOrdered(file -> {
                        FileUtils.loadImageFile(file, fileListTableModel, imgDataStorage);
                    });
                } catch (UnsupportedFlavorException | IOException ex) {
                    ImageDataStorage.getLOGGER().error("Unable to process drop", ex);
                }
            }
        });

    }

    public void mouseWheelMoved(MouseWheelEvent evt) {
        double scroll = evt.getPreciseWheelRotation();
        if (evt.isAltDown()) {
            double zoom = imagePane.getZoomFactor();
            double new_zoom = Range.between(DEFAULT_ZOOM, 1.0).fit(zoom + -scroll * 0.05);
            if (new_zoom == DEFAULT_ZOOM) {
                imagePane.setResizeStrategy(ResizeStrategy.RESIZE_TO_FIT);
            } else {
                imagePane.setResizeStrategy(ResizeStrategy.CUSTOM_ZOOM);
            }
            imagePane.setZoomFactor(new_zoom);

            double scrollable_width = imageScrollComponent.getHorizontalScrollBar().getMaximum() - imageScrollComponent.getHorizontalScrollBar().getWidth();
            double scrollable_height = imageScrollComponent.getVerticalScrollBar().getMaximum() - imageScrollComponent.getVerticalScrollBar().getHeight();
            double horizontal_pos = (double) evt.getX() / imageScrollComponent.getWidth() * scrollable_width;
            double vertical_pos = (double) evt.getY() / imageScrollComponent.getWidth() * scrollable_height;

            imageScrollComponent.getHorizontalScrollBar().setValue((int) horizontal_pos);
            imageScrollComponent.getVerticalScrollBar().setValue((int) vertical_pos);
        } else if (evt.isControlDown()) {
            int val = imageScrollComponent.getHorizontalScrollBar().getValue();
            imageScrollComponent.getHorizontalScrollBar().setValue((int) (val + scroll * 40));
        } else {
            int val = imageScrollComponent.getVerticalScrollBar().getValue();
            imageScrollComponent.getVerticalScrollBar().setValue((int) (val + scroll * 40));
        }
    }

    public String getNewClickType() {
        return (String) pointAddTypeSelect.getSelectedItem();
    }

    public ImageViewer getImagePane() {
        return imagePane;
    }

    public boolean hasOverlay() {
        return hasOverlay;
    }

    public void setHasOverlay(boolean hasOverlay) {
        this.hasOverlay = hasOverlay;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainPanel = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        tagTable = new TypeTable(imgDataStorage);
        interactionPanel = new javax.swing.JPanel();
        imagePanel = new javax.swing.JPanel();
        inferButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        pointAddTypeSelect = new javax.swing.JComboBox<>();
        opacitySlider = new javax.swing.JSlider();
        jLabel2 = new javax.swing.JLabel();
        interactiveModeButton = new javax.swing.JButton();
        exportButton = new javax.swing.JButton();
        exportAllButton = new javax.swing.JButton();
        openFilesButton = new javax.swing.JButton();
        exportMergeButton = new javax.swing.JButton();
        jScrollPane3 = new javax.swing.JScrollPane();
        tagCountTable = new TypeCountTable(imgDataStorage);
        zoomInButton = new javax.swing.JButton();
        zoomOutButton = new javax.swing.JButton();
        jScrollPane4 = new javax.swing.JScrollPane();
        fileListTable = new FileTable(imgDataStorage);
        inferAllButton = new javax.swing.JButton();
        selectAllLabel = new javax.swing.JCheckBox();
        jLabel6 = new javax.swing.JLabel();
        secRateLabel = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        pcdRateLabel = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        angleCalcButton = new javax.swing.JButton();
        angleAverage = new javax.swing.JLabel();
        angleStd = new javax.swing.JLabel();
        mainBar = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        newProjectMenuItem = new javax.swing.JMenuItem();
        loadItem = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JPopupMenu.Separator();
        saveItem = new javax.swing.JMenuItem();
        saveAsItem = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        saveCacheItem = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JPopupMenu.Separator();
        restoreItem = new javax.swing.JMenuItem();
        jMenu2 = new javax.swing.JMenu();
        jMenuItem1 = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        setFont(new java.awt.Font("Bodoni MT", 0, 14)); // NOI18N
        setMaximumSize(new java.awt.Dimension(0, 0));
        setMinimumSize(new java.awt.Dimension(1366, 780));
        setName("mainFrame"); // NOI18N
        setPreferredSize(new java.awt.Dimension(1366, 780));
        setSize(new java.awt.Dimension(1366, 780));

        mainPanel.setMaximumSize(new java.awt.Dimension(1366, 690));
        mainPanel.setName("mainPanel"); // NOI18N
        mainPanel.setPreferredSize(new java.awt.Dimension(1366, 690));

        jScrollPane1.setName("jScrollPane1"); // NOI18N

        tagTable.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 2));
        tagTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "colPoint", "", "Type", "Angle", "Off."
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Object.class, java.lang.Object.class, java.lang.Object.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, true, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        tagTable.setColumnSelectionAllowed(true);
        tagTable.setGridColor(new java.awt.Color(255, 255, 255));
        tagTable.setName("tagTable"); // NOI18N
        tagTable.setRowHeight(30);
        tagTable.setRowMargin(2);
        tagTable.setSelectionBackground(new java.awt.Color(255, 102, 102));
        tagTable.getTableHeader().setReorderingAllowed(false);
        jScrollPane1.setViewportView(tagTable);
        tagTable.getColumnModel().getSelectionModel().setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        if (tagTable.getColumnModel().getColumnCount() > 0) {
            tagTable.getColumnModel().getColumn(0).setResizable(false);
            tagTable.getColumnModel().getColumn(0).setPreferredWidth(0);
            tagTable.getColumnModel().getColumn(1).setResizable(false);
            tagTable.getColumnModel().getColumn(1).setPreferredWidth(25);
            tagTable.getColumnModel().getColumn(2).setResizable(false);
            tagTable.getColumnModel().getColumn(2).setPreferredWidth(100);
            tagTable.getColumnModel().getColumn(3).setResizable(false);
            tagTable.getColumnModel().getColumn(3).setPreferredWidth(40);
            tagTable.getColumnModel().getColumn(4).setResizable(false);
            tagTable.getColumnModel().getColumn(4).setPreferredWidth(35);
        }
        tagTable.getColumnModel().getColumn(0).setMinWidth(0);
        tagTable.getColumnModel().getColumn(0).setMaxWidth(0);

        interactionPanel.setBackground(new java.awt.Color(255, 255, 255));
        interactionPanel.setMinimumSize(new java.awt.Dimension(825, 647));
        interactionPanel.setName("interactionPanel"); // NOI18N
        interactionPanel.setPreferredSize(new java.awt.Dimension(825, 647));

        imagePanel.setBackground(new java.awt.Color(153, 153, 153));
        imagePanel.setMinimumSize(new java.awt.Dimension(825, 600));
        imagePanel.setName("imagePanel"); // NOI18N
        imagePanel.setPreferredSize(new java.awt.Dimension(825, 600));
        imagePanel.setLayout(new java.awt.GridLayout(1, 0));

        imagePanel.add(imagePaneComponent);

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("Bundle"); // NOI18N
        inferButton.setText(bundle.getString("MainFrame.inferButton.text")); // NOI18N
        inferButton.setName("inferButton"); // NOI18N
        inferButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                inferButtonActionPerformed(evt);
            }
        });

        jLabel1.setText(bundle.getString("MainFrame.jLabel1.text")); // NOI18N
        jLabel1.setName("jLabel1"); // NOI18N

        ArrayList<String> arr = imgDataStorage.getTypeConfigList();
        String[] array = arr.toArray(new String[arr.size()]);
        pointAddTypeSelect.setModel(new javax.swing.DefaultComboBoxModel<>(array));
        pointAddTypeSelect.setBackground(imgDataStorage.getColor(imgDataStorage.getTypeConfigList().get(0)));
        pointAddTypeSelect.setName("pointAddTypeSelect"); // NOI18N
        pointAddTypeSelect.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pointAddTypeSelectActionPerformed(evt);
            }
        });

        opacitySlider.setBackground(new java.awt.Color(255, 255, 255));
        opacitySlider.setFont(new java.awt.Font("Dialog", 1, 10)); // NOI18N
        opacitySlider.setForeground(new java.awt.Color(255, 51, 51));
        opacitySlider.setMajorTickSpacing(10);
        opacitySlider.setMinorTickSpacing(10);
        opacitySlider.setPaintTicks(true);
        opacitySlider.setSnapToTicks(true);
        opacitySlider.setValue(100);
        opacitySlider.setName("opacitySlider"); // NOI18N
        opacitySlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                opacitySliderStateChanged(evt);
            }
        });

        jLabel2.setText(bundle.getString("MainFrame.jLabel2.text")); // NOI18N
        jLabel2.setName("jLabel2"); // NOI18N

        javax.swing.GroupLayout interactionPanelLayout = new javax.swing.GroupLayout(interactionPanel);
        interactionPanel.setLayout(interactionPanelLayout);
        interactionPanelLayout.setHorizontalGroup(
            interactionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(interactionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pointAddTypeSelect, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, Short.MAX_VALUE)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(opacitySlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, Short.MAX_VALUE)
                .addComponent(inferButton, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
            .addComponent(imagePanel, javax.swing.GroupLayout.DEFAULT_SIZE, 837, Short.MAX_VALUE)
        );
        interactionPanelLayout.setVerticalGroup(
            interactionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(interactionPanelLayout.createSequentialGroup()
                .addComponent(imagePanel, javax.swing.GroupLayout.DEFAULT_SIZE, 604, Short.MAX_VALUE)
                .addGap(9, 9, 9)
                .addGroup(interactionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(inferButton, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(interactionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(pointAddTypeSelect, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(opacitySlider, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(8, 8, 8))
        );

        inferButton.setEnabled(false);

        interactiveModeButton.setText(bundle.getString("MainFrame.interactiveModeButton.text")); // NOI18N
        interactiveModeButton.setName("interactiveModeButton"); // NOI18N
        interactiveModeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                interactiveModeButtonActionPerformed(evt);
            }
        });

        exportButton.setText(bundle.getString("MainFrame.exportButton.text")); // NOI18N
        exportButton.setName("exportButton"); // NOI18N
        exportButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportButtonActionPerformed(evt);
            }
        });

        exportAllButton.setText(bundle.getString("MainFrame.exportAllButton.text")); // NOI18N
        exportAllButton.setName("exportAllButton"); // NOI18N
        exportAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportAllButtonActionPerformed(evt);
            }
        });

        openFilesButton.setText(bundle.getString("MainFrame.openFilesButton.text")); // NOI18N
        openFilesButton.setName("openFilesButton"); // NOI18N
        openFilesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openFilesButtonActionPerformed(evt);
            }
        });

        exportMergeButton.setText(bundle.getString("MainFrame.exportMergeButton.text")); // NOI18N
        exportMergeButton.setName("exportMergeButton"); // NOI18N

        jScrollPane3.setName("jScrollPane3"); // NOI18N

        tagCountTable.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 2));
        tagCountTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "", "Typ"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Object.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        tagCountTable.setColumnSelectionAllowed(true);
        tagCountTable.setFocusable(false);
        tagCountTable.setName("tagCountTable"); // NOI18N
        tagCountTable.setRowSelectionAllowed(false);
        tagCountTable.getTableHeader().setReorderingAllowed(false);
        jScrollPane3.setViewportView(tagCountTable);
        tagCountTable.getColumnModel().getSelectionModel().setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        if (tagCountTable.getColumnModel().getColumnCount() > 0) {
            tagCountTable.getColumnModel().getColumn(0).setPreferredWidth(10);
            tagCountTable.getColumnModel().getColumn(1).setPreferredWidth(170);
        }
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        tagCountTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        tagCountTable.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        tagCountTable.setFont(new Font("Serif", Font.BOLD, 12));

        zoomInButton.setText(bundle.getString("MainFrame.zoomInButton.text")); // NOI18N
        zoomInButton.setName("zoomInButton"); // NOI18N
        zoomInButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomInButtonActionPerformed(evt);
            }
        });

        zoomOutButton.setText(bundle.getString("MainFrame.zoomOutButton.text")); // NOI18N
        zoomOutButton.setName("zoomOutButton"); // NOI18N
        zoomOutButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomOutButtonActionPerformed(evt);
            }
        });

        jScrollPane4.setName("jScrollPane4"); // NOI18N

        fileListTable.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 2));
        fileListTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Set Eval", "File Name", ""
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Boolean.class, java.lang.Object.class, java.lang.Object.class
            };
            boolean[] canEdit = new boolean [] {
                true, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        fileListTable.setColumnSelectionAllowed(true);
        fileListTable.setGridColor(new java.awt.Color(0, 0, 0));
        fileListTable.setName("fileListTable"); // NOI18N
        fileListTable.setRowHeight(30);
        fileListTable.setSelectionBackground(new java.awt.Color(216, 205, 150));
        fileListTable.getTableHeader().setReorderingAllowed(false);
        fileListTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                fileListTableMouseClicked(evt);
            }
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                fileListTableMouseEntered(evt);
            }
        });
        jScrollPane4.setViewportView(fileListTable);
        fileListTable.getColumnModel().getSelectionModel().setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        if (fileListTable.getColumnModel().getColumnCount() > 0) {
            fileListTable.getColumnModel().getColumn(0).setResizable(false);
            fileListTable.getColumnModel().getColumn(0).setPreferredWidth(50);
            fileListTable.getColumnModel().getColumn(1).setResizable(false);
            fileListTable.getColumnModel().getColumn(1).setPreferredWidth(200);
            fileListTable.getColumnModel().getColumn(2).setResizable(false);
            fileListTable.getColumnModel().getColumn(2).setPreferredWidth(5);
        }
        fileListTable.getSelectionModel().addListSelectionListener(new ListSelectionListener(){
            public void valueChanged(ListSelectionEvent event) {
                fileTableRowSelect(event);
            }
        });

        inferAllButton.setText(bundle.getString("MainFrame.inferAllButton.text")); // NOI18N
        inferAllButton.setMaximumSize(new java.awt.Dimension(128, 20));
        inferAllButton.setMinimumSize(new java.awt.Dimension(128, 20));
        inferAllButton.setName("inferAllButton"); // NOI18N
        inferAllButton.setPreferredSize(new java.awt.Dimension(128, 20));
        inferAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                inferAllButtonActionPerformed(evt);
            }
        });

        selectAllLabel.setText(bundle.getString("MainFrame.selectAllLabel.text")); // NOI18N
        selectAllLabel.setName("selectAllLabel"); // NOI18N
        selectAllLabel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectAllLabelActionPerformed(evt);
            }
        });

        jLabel6.setText("SD:");
        jLabel6.setName("jLabel6"); // NOI18N

        secRateLabel.setText("100.00");
        secRateLabel.setName("secRateLabel"); // NOI18N

        jLabel8.setText("%");
        jLabel8.setName("jLabel8"); // NOI18N

        jLabel9.setText("PD:");
        jLabel9.setName("jLabel9"); // NOI18N

        pcdRateLabel.setText("100.00");
        pcdRateLabel.setName("pcdRateLabel"); // NOI18N

        jLabel4.setText("%");
        jLabel4.setName("jLabel4"); // NOI18N

        angleCalcButton.setText("Calculate Angles");
        angleCalcButton.setName("angleCalcButton"); // NOI18N
        angleCalcButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                angleCalcButtonActionPerformed(evt);
            }
        });

        angleAverage.setText("Avg. Angle: 0");
        angleAverage.setName("angleAverage"); // NOI18N

        angleStd.setText("Std. Angle: 0");
        angleStd.setName("angleStd"); // NOI18N

        javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
        mainPanel.setLayout(mainPanelLayout);
        mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addGap(5, 5, 5)
                        .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addGroup(mainPanelLayout.createSequentialGroup()
                                .addComponent(openFilesButton, javax.swing.GroupLayout.PREFERRED_SIZE, 144, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(inferAllButton, javax.swing.GroupLayout.PREFERRED_SIZE, 144, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)))
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addGap(25, 25, 25)
                        .addComponent(selectAllLabel)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addComponent(zoomInButton, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(angleCalcButton, javax.swing.GroupLayout.PREFERRED_SIZE, 257, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(zoomOutButton, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(interactionPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 837, Short.MAX_VALUE))
                .addGap(6, 6, 6)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(exportAllButton, javax.swing.GroupLayout.PREFERRED_SIZE, 213, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 213, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 213, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(interactiveModeButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 213, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exportButton, javax.swing.GroupLayout.PREFERRED_SIZE, 213, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exportMergeButton, javax.swing.GroupLayout.PREFERRED_SIZE, 213, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addComponent(jLabel9)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pcdRateLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel4)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jLabel6)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(secRateLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel8))
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addComponent(angleAverage)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(angleStd)))
                .addGap(5, 5, 5))
        );
        mainPanelLayout.setVerticalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addGap(10, 10, 10)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(zoomInButton)
                    .addComponent(zoomOutButton)
                    .addComponent(selectAllLabel)
                    .addComponent(angleCalcButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addComponent(jScrollPane4)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(openFilesButton, javax.swing.GroupLayout.DEFAULT_SIZE, 46, Short.MAX_VALUE)
                            .addComponent(inferAllButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel6)
                            .addComponent(secRateLabel)
                            .addComponent(jLabel8)
                            .addComponent(jLabel9)
                            .addComponent(pcdRateLabel)
                            .addComponent(jLabel4))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(angleStd)
                            .addComponent(angleAverage))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 151, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(interactiveModeButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exportButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exportAllButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exportMergeButton))
                    .addComponent(interactionPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 651, Short.MAX_VALUE))
                .addGap(5, 5, 5))
        );

        interactiveModeButton.setEnabled(false);
        exportButton.setEnabled(false);
        exportAllButton.setEnabled(false);
        exportMergeButton.setEnabled(false);
        angleCalcButton.setEnabled(false);

        mainBar.setName("mainBar"); // NOI18N

        jMenu1.setText(bundle.getString("MainFrame.jMenu1.text")); // NOI18N
        jMenu1.setName("jMenu1"); // NOI18N

        newProjectMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        newProjectMenuItem.setText("New Project");
        newProjectMenuItem.setName("newProjectMenuItem"); // NOI18N
        newProjectMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newProjectMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(newProjectMenuItem);

        loadItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        loadItem.setText(bundle.getString("MainFrame.loadItem.text")); // NOI18N
        loadItem.setName("loadItem"); // NOI18N
        loadItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadItemActionPerformed(evt);
            }
        });
        jMenu1.add(loadItem);

        jSeparator3.setName("jSeparator3"); // NOI18N
        jMenu1.add(jSeparator3);

        saveItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        saveItem.setText(bundle.getString("MainFrame.saveItem.text")); // NOI18N
        saveItem.setName("saveItem"); // NOI18N
        saveItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveItemActionPerformed(evt);
            }
        });
        jMenu1.add(saveItem);

        saveAsItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.ALT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK));
        saveAsItem.setText(bundle.getString("MainFrame.saveAsItem.text")); // NOI18N
        saveAsItem.setName("saveAsItem"); // NOI18N
        saveAsItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveAsItemActionPerformed(evt);
            }
        });
        jMenu1.add(saveAsItem);

        jSeparator2.setName("jSeparator2"); // NOI18N
        jMenu1.add(jSeparator2);

        saveCacheItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.ALT_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        saveCacheItem.setText("Save Marked Annotations :)"); // NOI18N
        saveCacheItem.setName("saveCacheItem"); // NOI18N
        saveCacheItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveCacheItemActionPerformed(evt);
            }
        });
        jMenu1.add(saveCacheItem);

        jSeparator1.setName("jSeparator1"); // NOI18N
        jMenu1.add(jSeparator1);

        restoreItem.setText(bundle.getString("MainFrame.restoreItem.text")); // NOI18N
        restoreItem.setName("restoreItem"); // NOI18N
        restoreItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                restoreItemActionPerformed(evt);
            }
        });
        jMenu1.add(restoreItem);

        mainBar.add(jMenu1);

        jMenu2.setText(bundle.getString("MainFrame.jMenu2.text")); // NOI18N
        jMenu2.setName("jMenu2"); // NOI18N

        jMenuItem1.setText("Set Annotation Location");
        jMenuItem1.setName("jMenuItem1"); // NOI18N
        jMenu2.add(jMenuItem1);

        mainBar.add(jMenu2);

        setJMenuBar(mainBar);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 698, Short.MAX_VALUE)
        );

        setSize(new java.awt.Dimension(1382, 760));
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void restoreItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_restoreItemActionPerformed
        loadProject(new File(Paths.get(Paths.get("").toString() + "/temp.wip").toString()));
    }//GEN-LAST:event_restoreItemActionPerformed

    private void saveItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveItemActionPerformed

        if (fileListTableModel.getRowCount() == 0) {
            return;
        }

        if (savePath == null) {
            saveAsItemActionPerformed(evt);
        } else {
            imgDataStorage.saveProject(savePath, imgDataStorage.getImageObjectList());
        }
    }//GEN-LAST:event_saveItemActionPerformed

    private void saveAsItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveAsItemActionPerformed

        JFileChooser chooser;

        if (lastChoosePath == null) {
            chooser = new JFileChooser();
        } else {
            chooser = new JFileChooser(lastChoosePath.toString());
        }

        chooser.setSelectedFile(new File("file.pcd"));
        chooser.setFileFilter(new FileNameExtensionFilter("PCD Detector Project file", "pcd"));

        int userSelection = chooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File saveFile = chooser.getSelectedFile();
            if(!saveFile.toString().contains(".pcd"))
                saveFile = new File(saveFile.toString() + ".pcd");
            if (saveFile != null) {
                savePath = Paths.get(saveFile.getAbsolutePath());
                imgDataStorage.saveProject(savePath, imgDataStorage.getImageObjectList());
            }
        }

    }//GEN-LAST:event_saveAsItemActionPerformed

    private void loadItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadItemActionPerformed
        JFileChooser fc;

        if (lastChoosePath == null) {
            fc = new JFileChooser();
        } else {
            fc = new JFileChooser(lastChoosePath.toString());
        }

        fc.setMultiSelectionEnabled(false);
        fc.setAcceptAllFileFilterUsed(false);
        fc.addChoosableFileFilter(pcdfilter);
        if (fileSearchAccessory == null) {
            fileSearchAccessory = new FileSearchAccessory(fc);
        } else {
            fileSearchAccessory.setChooser(fc);
        }
        fc.setAccessory(fileSearchAccessory);
        int returnVal = fc.showOpenDialog(this);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            if (file != null) {
                lastChoosePath = Paths.get(file.getAbsolutePath());
                loadProject(file);
            }
        }
    }//GEN-LAST:event_loadItemActionPerformed

    private void saveCacheItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveCacheItemActionPerformed
        try {
            for (int i = 0; i < fileListTableModel.getRowCount(); i++) {
                if ((boolean) fileListTableModel.getValueAt(i, 0)) {
                    if (imgDataStorage.getImage(i).isInitialized()) {
                        FileUtils.saveCacheItem(imgDataStorage.getImage(i));
                    }
                }
            }
        } catch (IOException e) {
            ImageDataStorage.getLOGGER().error("Unable to create cache", e);
        }
    }//GEN-LAST:event_saveCacheItemActionPerformed

    private void inferAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_inferAllButtonActionPerformed

        ArrayList<Integer> idxList = new ArrayList<>();

        for (int i = 0; i < fileListTable.getRowCount(); i++) {
            if (fileListTable.getValueAt(i, 0).equals(true) && !imgDataStorage.isInitialized(i)) {
                idxList.add(i);
            }
        }

        imgDataStorage.inferImages(idxList);
    }//GEN-LAST:event_inferAllButtonActionPerformed

    // Selection in file list
    private void fileListTableMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_fileListTableMouseClicked
        if (SwingUtilities.isRightMouseButton(evt)) {
            int row = fileListTable.rowAtPoint(evt.getPoint());
            fileListTable.setRowSelectionInterval(row, row);
            FileListPopup pop = new FileListPopup(this, fileListTable, imgDataStorage, row);
            pop.show(fileListTable, evt.getX(), evt.getY());
        }
    }//GEN-LAST:event_fileListTableMouseClicked

    private void zoomOutButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomOutButtonActionPerformed
        double zoom = imagePane.getZoomFactor();
        double new_zoom = Range.between(DEFAULT_ZOOM, 1.0).fit(zoom - ZOOM_DIFF - 0.001);
        if (new_zoom == DEFAULT_ZOOM) {
            imagePane.setResizeStrategy(ResizeStrategy.RESIZE_TO_FIT);
            return;
        }
        imagePane.setZoomFactor(new_zoom);
    }//GEN-LAST:event_zoomOutButtonActionPerformed

    private void zoomInButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomInButtonActionPerformed
        double zoom = imagePane.getZoomFactor();
        double new_zoom = Range.between(DEFAULT_ZOOM, 1.0).fit(zoom + ZOOM_DIFF);
        if (imagePane.getResizeStrategy() == ResizeStrategy.RESIZE_TO_FIT) {
            imagePane.setResizeStrategy(ResizeStrategy.CUSTOM_ZOOM);
        }
        imagePane.setZoomFactor(new_zoom);
    }//GEN-LAST:event_zoomInButtonActionPerformed

    private void openFilesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openFilesButtonActionPerformed
        JFileChooser fc;

        if (lastChoosePath == null) {
            fc = new JFileChooser();
        } else {
            fc = new JFileChooser(lastChoosePath.toString());
        }

        fc.setMultiSelectionEnabled(true);
        fc.setAcceptAllFileFilterUsed(false);
        fc.addChoosableFileFilter(filter);
        if (fileSearchAccessory == null) {
            fileSearchAccessory = new FileSearchAccessory(fc);
        } else {
            fileSearchAccessory.setChooser(fc);
        }
        fc.setAccessory(fileSearchAccessory);
        int returnVal = fc.showOpenDialog(this);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File[] files = fc.getSelectedFiles();
            if (files.length > 0) {
                lastChoosePath = Paths.get(files[0].getAbsolutePath());
            }
            ArrayList<File> failedList = new ArrayList<>();

            for (File file : files) {
                if (!FileUtils.loadImageFile(file, fileListTableModel, imgDataStorage)) {
                    failedList.add(file);
                }
            }

            if (failedList.size() > 0) {
                String failedfiles = "";
                failedfiles = failedList.stream().map(file -> file.getName() + ", ").reduce(failedfiles, String::concat);
                failedfiles = failedfiles.substring(0, failedfiles.length() - 3);
                JOptionPane.showMessageDialog(this, "Nasledujici snimky se nepodarilo otevrit: " + failedfiles, "Zlyhani", JOptionPane.WARNING_MESSAGE);

                if (failedList.size() < files.length) {
                    exportAllButton.setEnabled(true);
                }
            } else {
                exportAllButton.setEnabled(true);
            }

        }
    }//GEN-LAST:event_openFilesButtonActionPerformed

    private void exportAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportAllButtonActionPerformed
        try {
            FileUtils.saveCSVMultiple(FileUtils.getCSVSaveLocation(this), imgDataStorage.getImageObjectList(), imgDataStorage.getTypeConfigList());
        } catch (IOException | NullPointerException e) {
            ImageDataStorage.getLOGGER().error("Unable to save CSV", e);
        }
    }//GEN-LAST:event_exportAllButtonActionPerformed

    private void exportButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportButtonActionPerformed
        try {
            FileUtils.saveCSVSingle(FileUtils.getCSVSaveLocation(this), imgDataStorage.getCounts(), imgDataStorage.getTypeConfigList());
        } catch (IOException | NullPointerException e) {
            ImageDataStorage.getLOGGER().error("Unable to save CSV", e);
        }
    }//GEN-LAST:event_exportButtonActionPerformed

    private void opacitySliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_opacitySliderStateChanged
        imgDataStorage.getCurrent().setPointsOpacity(opacitySlider.getValue() / 100.f);
    }//GEN-LAST:event_opacitySliderStateChanged

    private void inferButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_inferButtonActionPerformed
        listenerActive = false;
        boolean success = imgDataStorage.inferImage();

        saveProjectTemp();

        if (success) {
            exportButton.setEnabled(true);
            angleCalcButton.setEnabled(true);
            opacitySlider.setEnabled(true);
            interactiveModeButton.setEnabled(true);
            inferButton.setEnabled(false);
            imagePane.addOverlay(imgDataStorage.getOverlay(), 1);
            hasOverlay = true;
            loadTables();
            listenerActive = true;
            fileListTableModel.fireTableDataChanged();
            return;
        }

        hasOverlay = false;
    }//GEN-LAST:event_inferButtonActionPerformed

    private void interactiveModeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_interactiveModeButtonActionPerformed
        if (!imgDataStorage.getCurrent().isInitialized()) {
            return;
        }

        ArrayList<PcdPoint> points = imgDataStorage.getCurrent().getPointList();
        ArrayList<PcdPoint> filteredPoints = new ArrayList<>();

        points.stream().filter(point -> (point.getScore() <= Constant.SCORE_THRESHOLD)).forEachOrdered(point -> {
            filteredPoints.add(point);
        });

        InteractiveModeDialog dialog = new InteractiveModeDialog(this, filteredPoints, imgDataStorage.getImageObject(),
                imgDataStorage.getTypeConfigList(), imgDataStorage.getTypeIdentifierList());

        dialog.setVisible(true);

        filteredPoints.stream().filter(filteredPoint -> (filteredPoint.getType() == -1)).forEachOrdered(filteredPoint -> {
            imgDataStorage.remPoint(filteredPoint);
        });

        loadTables();

    }//GEN-LAST:event_interactiveModeButtonActionPerformed

    private void fileListTableMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_fileListTableMouseEntered
        CellEditor cellEditor = tagTable.getCellEditor();
        if (cellEditor != null) {
            if (cellEditor.getCellEditorValue() != null) {
                cellEditor.stopCellEditing();
            } else {
                cellEditor.cancelCellEditing();
            }
        }
    }//GEN-LAST:event_fileListTableMouseEntered

    private void selectAllLabelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectAllLabelActionPerformed
        boolean selected = selectAllLabel.isSelected();

        for (int i = 0; i < fileListTableModel.getRowCount(); i++) {
            fileListTableModel.setValueAt(selected, i, 0);
        }
    }//GEN-LAST:event_selectAllLabelActionPerformed

    private void pointAddTypeSelectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pointAddTypeSelectActionPerformed
        pointAddTypeSelect.setBackground(imgDataStorage.getColor((String) pointAddTypeSelect.getSelectedItem()));
        pointAddTypeSelect.transferFocusBackward();
    }//GEN-LAST:event_pointAddTypeSelectActionPerformed

    private void newProjectMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newProjectMenuItemActionPerformed
        if (hasOverlay) {
            imagePane.removeOverlay(imgDataStorage.getOverlay());
            hasOverlay = false;
        }

        imagePane.setImage(null);

        fileListTable.clearSelection();
        fileListTableModel.setRowCount(0);
        imgDataStorage.dispose();

        inferButton.setEnabled(false);
        interactiveModeButton.setEnabled(false);
        exportAllButton.setEnabled(false);
        exportButton.setEnabled(false);
        exportMergeButton.setEnabled(false);
        opacitySlider.setEnabled(false);

        savePath = null;

        imgDataStorage.clear();
        loadTables();
    }//GEN-LAST:event_newProjectMenuItemActionPerformed

    private void angleCalcButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_angleCalcButtonActionPerformed
        boolean success = imgDataStorage.initializeAngles();
        
        if(success){
            angleCalcButton.setEnabled(false);
            loadTables();
        }
    }//GEN-LAST:event_angleCalcButtonActionPerformed

    // Select file
    private void fileTableRowSelect(ListSelectionEvent e) {
        int selected = fileListTable.getSelectedRow();

        if (selected == -1 || selected == current_selected) {
            return;
        }

        listenerActive = false;
        tagTable.clearSelection();
        current_selected = selected;

        if (hasOverlay) {
            imagePane.removeOverlay(imgDataStorage.getOverlay());
            hasOverlay = false;
        }

        imagePane.setImage(imgDataStorage.getImageObject(selected));
        imagePane.setResizeStrategy(ResizeStrategy.RESIZE_TO_FIT);
        imagePane.setZoomFactor(DEFAULT_ZOOM);
        opacitySlider.setValue(100);

        if (imgDataStorage.isInitialized()) {
            exportButton.setEnabled(true);
            opacitySlider.setEnabled(true);
            interactiveModeButton.setEnabled(true);
            inferButton.setEnabled(false);
            imagePane.addOverlay(imgDataStorage.getOverlay(), 1);
            hasOverlay = true;
            if(!imgDataStorage.isAngleInitialized())
                angleCalcButton.setEnabled(true);
        } else {
            opacitySlider.setEnabled(false);
            interactiveModeButton.setEnabled(false);
            inferButton.setEnabled(true);
            angleCalcButton.setEnabled(false);
        }

        loadTables();

        listenerActive = true;
    }

    public JTable getFileListTable() {
        return fileListTable;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel angleAverage;
    private javax.swing.JButton angleCalcButton;
    private javax.swing.JLabel angleStd;
    private javax.swing.JButton exportAllButton;
    private javax.swing.JButton exportButton;
    private javax.swing.JButton exportMergeButton;
    private javax.swing.JTable fileListTable;
    private javax.swing.JPanel imagePanel;
    private javax.swing.JButton inferAllButton;
    private javax.swing.JButton inferButton;
    private javax.swing.JPanel interactionPanel;
    private javax.swing.JButton interactiveModeButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenu jMenu2;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator2;
    private javax.swing.JPopupMenu.Separator jSeparator3;
    private javax.swing.JMenuItem loadItem;
    private javax.swing.JMenuBar mainBar;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JMenuItem newProjectMenuItem;
    private javax.swing.JSlider opacitySlider;
    private javax.swing.JButton openFilesButton;
    private javax.swing.JLabel pcdRateLabel;
    private javax.swing.JComboBox<String> pointAddTypeSelect;
    private javax.swing.JMenuItem restoreItem;
    private javax.swing.JMenuItem saveAsItem;
    private javax.swing.JMenuItem saveCacheItem;
    private javax.swing.JMenuItem saveItem;
    private javax.swing.JLabel secRateLabel;
    private javax.swing.JCheckBox selectAllLabel;
    private javax.swing.JTable tagCountTable;
    private javax.swing.JTable tagTable;
    private javax.swing.JButton zoomInButton;
    private javax.swing.JButton zoomOutButton;
    // End of variables declaration//GEN-END:variables

    public void loadTables() {
        DefaultTableModel pointModel = (DefaultTableModel) tagTable.getModel();

        listenerActive = false;
        tagTable.getSelectionModel().clearSelection();

        pointModel.setRowCount(0);

        loadCountTable();

        if (imgDataStorage.getCurrent() == null) {
            return;
        }

        if (!listenerAdded) {

            tagTable.getSelectionModel().addListSelectionListener((ListSelectionEvent event) -> {
                if (listenerActive) {
                    DefaultTableModel pointModel1 = (DefaultTableModel) tagTable.getModel();
                    int idx = tagTable.getSelectedRow();
                    mouseListenerClick.setSelection((PcdPoint) pointModel1.getValueAt(idx, 0));
                }
            });

            tagTable.getModel().addTableModelListener((TableModelEvent e) -> {
                if (listenerActive) {
                    if (e.getType() == TableModelEvent.UPDATE && e.getColumn() == 2) {
                        int idx = e.getFirstRow();
                        if (idx == -1) {
                            return;
                        }
                        PcdPoint p = (PcdPoint) tagTable.getValueAt(idx, 0);
                        if ("None".equals((String) tagTable.getValueAt(idx, 2))) {
                            SwingUtilities.invokeLater(() -> {
                                mouseListenerClick.remPoint(p);
                            });
                            saveProjectTemp();
                            return;
                        }
                        imgDataStorage.setPointType(p, (String) tagTable.getValueAt(idx, 2));
                        saveProjectTemp();
                        loadCountTable();
                        imgDataStorage.getCurrent().getOverlay().repaint();
                    }
                }
            });

            TableColumn comboColumn = tagTable.getColumnModel().getColumn(2);
            ArrayList<String> cfg = imgDataStorage.getTypeConfigList();
            JComboBox editor = new JComboBox();

            cfg.forEach(string -> {
                editor.addItem(string);
            });

            editor.addItem("None");

            comboColumn.setCellEditor(new DefaultCellEditor(editor));

            listenerAdded = true;
        }

        ArrayList<PcdPoint> pointList = imgDataStorage.getCurrent().getPointList();

        if (pointList == null || pointList.isEmpty()) {
            pointModel.setRowCount(0);
            return;
        }

        Object[] pointArray = Stream.concat(
                pointList.stream().filter(s -> s.getScore() <= Constant.SCORE_THRESHOLD).sorted(Comparator.comparing(PcdPoint::getType)),
                pointList.stream().filter(s -> s.getScore() > Constant.SCORE_THRESHOLD).sorted(Comparator.comparing(PcdPoint::getType))
        ).toArray();

        DecimalFormat df = new DecimalFormat("#.#");
        
        for (Object pt : pointArray) {
            double angle = ((PcdPoint) pt).getAngle();
            double offset = Math.abs(angle - imgDataStorage.getCurrent().getAvgAngle());
            pointModel.addRow(new Object[]{pt, "", ((PcdPoint) pt).getTypeName(), df.format(angle), df.format(offset)});
        }

        listenerActive = true;
    }

    private void loadCountTable() {
        DefaultTableModel pointCountModel = (DefaultTableModel) tagCountTable.getModel();
        pointCountModel.setRowCount(0);

        if (imgDataStorage.getCurrent() == null) {
            pcdRateLabel.setText("0.00");
            secRateLabel.setText("0.00");
            angleAverage.setText("Avg. angle: 0");
            angleStd.setText("Std. angle: 0");
            return;
        }

        if (imgDataStorage.getCurrent().isInitialized()) {

            ArrayList<AtomicInteger> counts = imgDataStorage.getCounts();
            ArrayList<String> names = imgDataStorage.getTypeConfigList();

            for (int i = 0; i < counts.size(); i++) {
                pointCountModel.addRow(new Object[]{counts.get(i), names.get(i)});
            }
            
            pcdRateLabel.setText(imgDataStorage.getPcdRate(counts));
            secRateLabel.setText(imgDataStorage.getSecRate(counts));
            
            DecimalFormat df = new DecimalFormat("#.##");
            
            if(imgDataStorage.getCurrent().isAngleInitialized()){
                angleAverage.setText("Avg. angle: " + df.format(imgDataStorage.getCurrent().getAvgAngle()));
                angleStd.setText("Std. angle: " + df.format(imgDataStorage.getCurrent().getStdAngle()));
            }
        }

    }

    public JTable getTagTable() {
        return tagTable;
    }

    public JTable getTagCountTable() {
        return tagCountTable;
    }

    public void saveProjectTemp() {
        imgDataStorage.saveProject(new File("temp.wip").toPath(), imgDataStorage.getImageObjectList());
    }

    public void loadProject(File file) {
        if (hasOverlay) {
            imagePane.removeOverlay(imgDataStorage.getOverlay());
            hasOverlay = false;
        }

        imagePane.setImage(null);

        fileListTableModel.setNumRows(0);
        ArrayList<ImageDataObject> deserlist = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file); ObjectInputStream ois = new ObjectInputStream(fis)) {
            deserlist = (ArrayList<ImageDataObject>) ois.readObject();
        } catch (ClassNotFoundException | IOException e) {
            ImageDataStorage.getLOGGER().error("Unable to read object on project load", e);
            JOptionPane.showMessageDialog(this, "Project file doesn't exist", "Error", JOptionPane.ERROR_MESSAGE);
        }

        imgDataStorage.setImageObjectList(deserlist);

        for (ImageDataObject imageDataObject : deserlist) {

            fileListTableModel.addRow(new Object[]{false, imageDataObject.getImageName(), ""});
        }

        loadTables();

    }
}
