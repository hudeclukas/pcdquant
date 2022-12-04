package pcd.data;

import pcd.gui.MainFrame;
import pcd.gui.dialog.AngleLoadingDialog;
import pcd.gui.dialog.LoadingDialog;
import pcd.gui.dialog.LoadingMultipleDialogGUI;
import pcd.imageviewer.Overlay;
import pcd.python.PythonProcess;
import pcd.utils.AngleWrapper;
import pcd.utils.FileUtils;
import pcd.utils.PcdColor;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pcd.gui.base.ImgFileFilter;

/**
 *
 * @author Tibor Sloboda Holds and manipulates {@link ImageDataObject}, as well
 * as utilizing and creating new windows with the {@link PythonProcess} linked
 * inside them to retrieve new data from Python to update the
 * {@link ImageDataObject}
 */
public class ImageDataStorage {

    private static final Logger LOGGER = LogManager.getLogger(ImageDataStorage.class);

    /**
     * A list of currently saved {@link ImageDataObject}
     */
    private ArrayList<ImageDataObject> imageList = new ArrayList<>();
    /**
     * The currently active {@link ImageDataObject} being displayed and
     * manipulated in the main GUI window.
     *
     * @see MainFrame
     */
    private ImageDataObject current;

    /**
     * @see pcd.Initializer#typeConfigList
     */
    private final ArrayList<String> typeConfigList;
    /**
     * @see pcd.Initializer#typeIdentifierList
     */
    private final ArrayList<Integer> typeIdentifierList;
    /**
     * @see pcd.Initializer#typeIconList
     */
    private final ArrayList<String> typeIconList;
    /**
     * @see pcd.Initializer#typeTypeList
     */
    private final ArrayList<String> typeTypeList;
    /**
     * The python process instance
     *
     * @see PythonProcess
     */
    private final PythonProcess pyproc;
    /**
     * An {@link ImageDataObject} factory class instance
     *
     * @see ImageDataObjectFactory
     */
    private final ImageDataObjectFactory imgFactory;
    /**
     * A reference to the main GUI window screen
     *
     * @see MainFrame
     */
    private MainFrame parentFrame;

    private final java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("Bundle", Locale.getDefault());

    /**
     * Initializes the {@link ImageDataStorage} with configuration fields and
     * gets an instance for the {@link PythonProcess} and
     * {@link ImageDataObjectFactory}
     *
     * @param typeConfigList A list of cilia type name identifiers
     * @param typeIdentifierList A list of cilia type numeric IDs
     * @param typeIconList A optional (can be empty) list of icons to replace
     * colors for point markers
     * @param typeTypeList A list of the kind of types a cilia is
     * (healthy/unhealthy)
     */
    public ImageDataStorage(ArrayList<String> typeConfigList, ArrayList<Integer> typeIdentifierList, ArrayList<String> typeIconList, ArrayList<String> typeTypeList) {
        this.typeConfigList = typeConfigList;
        this.typeIdentifierList = typeIdentifierList;
        this.typeIconList = typeIconList;
        this.typeTypeList = typeTypeList;
        pyproc = PythonProcess.getInstance();
        imgFactory = new ImageDataObjectFactory(this);
    }

    /**
     *
     * @return returns the {@link ImageDataStorage#typeConfigList}
     */
    public ArrayList<String> getTypeConfigList() {
        return typeConfigList;
    }

    /**
     *
     * @return returns the {@link ImageDataStorage#typeIdentifierList}
     */
    public ArrayList<Integer> getTypeIdentifierList() {
        return typeIdentifierList;
    }

    public void skipDetection(boolean skip) {
        if (skip) {
            pyproc.deactivateDetection();
        } else {
            pyproc.activateDetection();
        }
    }

    /**
     * Stops the python process
     */
    public void stopProcess() {
        pyproc.stop();
    }

    /**
     * Adds a new image object to {@link ImageDataStorage#imageList} based on
     * image path.
     *
     * @param path a {@link String} path to the image. Should be absolute.
     */
    public void addImage(String path) {
        imgFactory.addImage(path);
    }

    /**
     * Retrieves the {@link BufferedImage} of the image associated with
     * {@link ImageDataObject}. It also updates the
     * {@link ImageDataStorage#current} image object based on the supplied
     * index. If the path to the image isn't valid, it will offer to navigate to
     * a new image file. This also updates the associated image file inside
     * {@link ImageDataObject}
     *
     * @see ImageDataObject
     * @param index The index of the {@link ImageDataObject} in
     * {@link ImageDataStorage#imageList}
     * @return a {@link BufferedImage} of the associated image, or null if
     * invalid file
     */
    public BufferedImage getBufferedImage(int index) {
        String path = getAndUpdateCurrentImage(index).getImgPath();

        File imgFile = new File(path);
        if (!imgFile.exists() || !imgFile.canRead()) {
            String imageNotFound = bundle.getString("ImageDataStorage.imageNotFound");
            String warning = bundle.getString("ImageDataStorage.warning");
            int returnValue = JOptionPane.showConfirmDialog(parentFrame, imageNotFound, warning, JOptionPane.YES_NO_OPTION);

            if (returnValue == JOptionPane.YES_OPTION) {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileFilter(new ImgFileFilter());
                int returnVal = chooser.showOpenDialog(parentFrame);

                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    File chosen = chooser.getSelectedFile();

                    return current.loadImage(chosen.getAbsolutePath());
                }
            } else {
                return null;
            }
        }

        return current.loadImage();
    }

    /**
     * Load the image of the currently active {@link ImageDataObject}
     *
     * @return a {@link BufferedImage} of the associated image to
     * {@link ImageDataStorage#current}, or null if invalid file
     */
    public BufferedImage getBufferedImage() {
        return current.loadImage();
    }

    /**
     * Retrieves an {@link ImageDataObject} based on index from
     * {@link ImageDataStorage#imageList} and updates
     * {@link ImageDataStorage#current} to it.
     *
     * @param index index of the image object to retrieve
     * @return the retrieved {@link ImageDataObject}
     */
    public ImageDataObject getAndUpdateCurrentImage(int index) {
        current = imageList.get(index);
        return current;
    }

    /**
     * Retrieves an {@link ImageDataObject} based on index from
     * {@link ImageDataStorage#imageList}.
     *
     * @param index index of the image object to retrieve
     * @return the retrieved {@link ImageDataObject}
     */
    public ImageDataObject getImage(int index) {
        return imageList.get(index);
    }

    /**
     * Retrieves the currently active {@link ImageDataObject}.
     *
     * @return the retrieved {@link ImageDataStorage#current} or null if never
     * assigned
     */
    public ImageDataObject getCurrent() {
        return current;
    }

    /**
     * Retrieves the {@link PointOverlay} of the current active
     * {@link ImageDataObject}
     *
     * @see PointOverlay
     * @return the retrieved overlay or null if uninitialized
     */
    public Overlay getOverlay() {
        if (current == null || !current.isInitialized()) {
            return null;
        }

        return current.getOverlay();
    }

    /**
     * Removes an {@link ImageDataObject} from
     * {@link ImageDataStorage#imageList}.
     *
     * @param index the index of the image object to be removed
     */
    public void deleteImage(int index) {
        imageList.remove(index);
    }

    /**
     * Adds a new {@link ImageDataObject} to {@link ImageDataStorage#imageList}
     *
     * @param img the {@link ImageDataObject} to be added
     */
    public void addImage(ImageDataObject img) {
        imageList.add(img);
    }

    /**
     * Uses a stream to find whether passed file matches any image path of
     * {@link ImageDataObject}.
     *
     * @param f the file to be checked
     * @return true if a match is found
     */
    public boolean checkOpened(File f) {
        return (imageList.parallelStream().map(
                img -> {
                    try {
                        return img.fileMatch(f.getPath());
                    } catch (IOException e) {
                        return false;
                    }
                }
        ).collect(Collectors.toList())).stream().anyMatch(e -> e);
    }

    /**
     * Sets the parent GUI frame, specifically an instance of {@link MainFrame}
     *
     * @param aThis the parent frame
     */
    public void setFrame(MainFrame aThis) {
        this.parentFrame = aThis;
    }

    /**
     * Complementary to {@link ImageDataStorage#addPoint(pcd.data.PcdPoint)}
     * for when we don't know the point's type ID, just the name of the type.
     * It adds a new point after determining the ID of the type name, and sets
     * the name to the point.
     *
     * @param pcdPoint the point to be added
     * @param newClickType the {@link String} containing the name of the cilia type
     */
    public void addPoint(PcdPoint pcdPoint, String newClickType) {
        pcdPoint.setType(typeIdentifierList.get(typeConfigList.indexOf(newClickType)));
        pcdPoint.setTypeName(newClickType);
        addPoint(pcdPoint);
    }

    /**
     * Retrieves a {@link PcdColor} based on the point
     *
     * @param p the {@link PcdPoint} for which to retrieve the color
     * @return the associated color
     */
    public PcdColor getColor(PcdPoint p) {
        int idx = typeIdentifierList.indexOf(p.getType());
        String s = typeIconList.get(idx);
        return parseColor(s);
    }

    /**
     * Retrieves a {@link PcdColor} based on the cilia type specified in
     * configuration
     *
     * @param typeName the {@link String} matching one of the type names in
     * configuration
     * @return the associated color
     */
    public PcdColor getColor(String typeName) {
        return parseColor(typeIconList.get(typeConfigList.indexOf(typeName)));
    }

    private PcdColor parseColor(String s) {
        if (s.contains(".rgb")) {
            int r = Integer.parseInt(s.substring(0, 2), 16);
            int g = Integer.parseInt(s.substring(2, 4), 16);
            int b = Integer.parseInt(s.substring(4, 6), 16);
            return new PcdColor(r, g, b);
        }

        return null;
    }

    /**
     * Gets an icon based on the path of the associated point type from the icon
     * list
     *
     * @deprecated
     * @param point the point for which to retrieve an icon
     * @return the loaded {@link BufferedImage}
     */
    public BufferedImage getIcon(PcdPoint point) {
        BufferedImage img = null;
        try {
            img = ImageIO.read(new File("./icons/" + typeIconList.get(typeIdentifierList.indexOf(point.getType()))));
        } catch (IOException e) {
            String utli = "Unable to load icon";

            LOGGER.error(utli, e);
            String CRFI = bundle.getString("ImageDataStorage.crfi");
            String ERR = bundle.getString("ImageDataStorage.err");
            JOptionPane.showMessageDialog(parentFrame, CRFI, ERR, JOptionPane.ERROR_MESSAGE);
        }

        return img;
    }

    /**
     * Gets an icon based on the path of the identifier of a cilia type
     *
     * @deprecated
     * @param identifier the type ID for which to get an icon
     * @return the loaded {@link BufferedImage}
     */
    public BufferedImage getIcon(String identifier) {

        BufferedImage img = null;
        try {
            img = ImageIO.read(new File("./icons/" + typeIconList.get(typeConfigList.indexOf(identifier))));
        } catch (IOException e) {
            LOGGER.error("", e);
            String CRFI = bundle.getString("ImageDataStorage.crfi");
            String ERR = bundle.getString("ImageDataStorage.err");
            JOptionPane.showMessageDialog(parentFrame, CRFI, ERR, JOptionPane.ERROR_MESSAGE);
        }

        return img;
    }

    /**
     * Gets the name of the type in the passed point based on its associated ID
     * from the config.
     *
     * @param p the point for which to get the type name of
     * @return the associated name of the type
     */
    public String getPointTypeName(PcdPoint p) {
        int idx = typeIdentifierList.indexOf(p.getType());
        return typeConfigList.get(idx);
    }

    /**
     * Retrieves the ID of a type name.
     *
     * @param s the name of the type
     * @return the type ID
     */
    public int getPointIdentifier(String s) {
        int idx = typeConfigList.indexOf(s);
        if (idx == -1) {
            return idx;
        }
        return typeIdentifierList.get(idx);
    }

    /**
     * Counts the total amounts of each cilia type by occurrence.
     *
     * @return an {@link ArrayList} containing {@link AtomicInteger} ordered the
     * same as {@link ImageDataStorage#typeConfigList} containing the number of
     * points that have this identifier, each.
     */
    public ArrayList<AtomicInteger> getCounts() {
        ArrayList<AtomicInteger> counts = new ArrayList<>();
        typeConfigList.forEach(_item -> counts.add(new AtomicInteger(0)));

        current.getPointTypes().forEach(type -> counts.get(typeIdentifierList.indexOf(type)).incrementAndGet());

        return counts;
    }

    /**
     * The same as {@link ImageDataStorage#getCounts()} but for a specific
     * {@link ImageDataObject}
     *
     * @param imgObj the image object for which to retrieve counts
     * @return same as {@link ImageDataStorage#getCounts()}
     */
    public ArrayList<AtomicInteger> getCounts(ImageDataObject imgObj) {
        ArrayList<AtomicInteger> counts = new ArrayList<>();
        typeConfigList.forEach(_item -> counts.add(new AtomicInteger(0)));

        imgObj.getPointTypes().forEach(type -> counts.get(typeIdentifierList.indexOf(type)).incrementAndGet());

        return counts;
    }

    /**
     * Calculates the primary defect rate. It is important this is calculated
     * correctly for diagnosis, and it must retrieve the same value regardless
     * of platform, therefore it has the <b>strictfp</b> modifier to calculate
     * these values precisely and in the same manner on all platforms.
     *
     * @param counts an {@link ArrayList} of {@link AtomicInteger} matching the
     * {@link ImageDataStorage#typeConfigList} indexing order
     * @return a formatted string, rounding the resulting value to 2 decimal
     * places for displaying in the GUI
     */
    strictfp public String getPcdRate(ArrayList<AtomicInteger> counts) {
        DecimalFormat df = new DecimalFormat("#.##");
        double primary = Math.ulp(1.0);
        double normal = Math.ulp(1.0);

        for (int i = 0; i < counts.size(); i++) {
            int num = counts.get(i).get();
            switch (typeTypeList.get(i)) {
                default:
                    break;
                case "n":
                    normal += num;
                    break;
                case "p":
                    primary += num;
                    break;
            }
        }

        return df.format(primary * 100 / (normal + primary));
    }

    /**
     * Calculates the secondary defect rate. It is identical to
     * {@link ImageDataStorage#getPcdRate} but for secondary defects.
     *
     * @param counts an {@link ArrayList} of {@link AtomicInteger} matching the
     * {@link ImageDataStorage#typeConfigList} indexing order
     * @return a formatted string, rounding the resulting value to 2 decimal
     * places for displaying in the GUI
     */
    strictfp public String getSecRate(ArrayList<AtomicInteger> counts) {
        DecimalFormat df = new DecimalFormat("#.##");
        double secondary = Math.ulp(1.0);
        double normal = Math.ulp(1.0);

        for (int i = 0; i < counts.size(); i++) {
            int num = counts.get(i).get();
            switch (typeTypeList.get(i)) {
                default:
                    break;
                case "n":
                    normal += num;
                    break;
                case "s":
                    secondary += num;
                    break;
            }
        }

        return df.format(secondary * 100 / (normal + secondary));
    }

    /**
     * Checks whether the active ({@link ImageDataStorage#current}) image object
     * is point initialized.
     *
     * @return true if initialized, and false if current image is null or not
     * initialized
     */
    public boolean isInitialized() {
        if (current == null) {
            return false;
        }
        return current.isInitialized();
    }

    /**
     * Checks whether the active ({@link ImageDataStorage#current}) image object
     * is angle initialized.
     *
     * @return true if initialized, and false if current image is null or not
     * initialized
     */
    public boolean isAngleInitialized() {
        if (current == null) {
            return false;
        }
        return current.isAngleInitialized();
    }

    /**
     * Adds a new point to the current active {@link ImageDataObject}
     *
     * @see ImageDataObject#addPoint(pcd.data.PcdPoint)
     * @param pcdPoint the {@link PcdPoint} to be added
     */
    public void addPoint(PcdPoint pcdPoint) {
        current.addPoint(pcdPoint);
    }

    /**
     * Removes a point form the current active {@link ImageDataObject}
     *
     * @see ImageDataObject#remPoint(pcd.data.PcdPoint)
     * @param pcdPoint the {@link PcdPoint} to be removed
     */
    public void remPoint(PcdPoint pcdPoint) {
        current.remPoint(pcdPoint);
    }

    /**
     * Removes the currently image object from
     * {@link ImageDataStorage#imageList} and sets
     * {@link ImageDataStorage#current} to null.
     */
    public void dispose() {
        if (current != null) {
            imageList.remove(current);
        }
        current = null;
    }

    /**
     * Returns the reference to the whole list of {@link ImageDataObject}. It
     * breaks encapsulation but is safe. Data is saved continuously so it can be
     * easily restored, and protections are in place to prevent overflow
     * exploits and stalling of the application.
     *
     * @see ImageDataObject#addPoint(pcd.data.PcdPoint)
     * @see ImageDataObject#getPointList()
     * @see ImageDataObject#setPointList(java.util.ArrayList)
     * @return a {@link List} of {@link ImageDataObject}
     */
    public final List<ImageDataObject> getImageObjectList() {
        return imageList;
    }

    /**
     * Gets all the image names of currently saved {@link ImageDataObject}
     *
     * @return an {@link ArrayList} of associated image names
     */
    public ArrayList<String> getImageNames() {
        ArrayList<String> strList = new ArrayList<>();

        imageList.forEach(imageDataObject -> strList.add(imageDataObject.getImageName()));

        return strList;
    }

    /**
     * Replaces the {@link ImageDataStorage#imageList} with a new one. This is
     * for the purpose of loading a project file. The safety is checked to
     * prevent overflow by checking the point amount in each image object.
     *
     * @param list the {@link ImageDataObject} {@link ArrayList} to replace the
     * current one
     */
    public void setImageObjectList(ArrayList<ImageDataObject> list) {
        if (list == null) {
            return;
        }

        boolean safe = true;
        for (ImageDataObject imgObj : list) {
            safe &= imgObj != null;
            if(safe){
                if(imgObj.getPointList() != null)
                    safe &= imgObj.getPointList().size() <= 400;
            }
            else
                break;
        }
        
        if (!safe) {
            return;
        }
        imageList = list;
        current = null;
    }

    /**
     * Check whether a specific {@link ImageDataObject} is initialized.
     *
     * @param index the index of the image to check
     * @return true if initialized, or false if index exceeds maximum or not
     * initialized
     */
    public boolean isInitialized(int index) {
        if (imageList == null || index >= imageList.size()) {
            return false;
        }
        return imageList.get(index).isInitialized();
    }

    /**
     * Used to display a loading window and initialize retrieval of angles using
     * Python. It also calculates the average from the positive normalized
     * angles, and the number of them, before passing it to
     * {@link ImageDataObject} for calculation of standard deviation and saving.
     *
     * @return true if method succeeded or already initialized, or false it
     * something went wrong or not point initialized
     */
    public boolean initializeAngles() {
        if(current == null){
            return false;
        }
        
        /*if (current.isAngleInitialized()) {
            return true;
        }*/

        if (!current.isInitialized()) {
            return false;
        }

        AngleLoadingDialog loading = new AngleLoadingDialog(parentFrame, current.getImgPath(), current.getRawPointList());
        loading.setLocationRelativeTo(parentFrame);

        AngleWrapper angleWrapper = loading.showDialog();
        if (angleWrapper == null) {
            return false;
        }

        current.mapAngles(angleWrapper);

        boolean result = current.isAngleInitialized();
        String ula = bundle.getString("ImageDataStorage.ula");
        String ERR = bundle.getString("ImageDataStorage.err");
        if (!result) {
            JOptionPane.showMessageDialog(parentFrame, ula, ERR, JOptionPane.ERROR_MESSAGE);
        }

        return result;

    }

    /**
     * Used to display a loading window and initialize retrieval of points using
     * Python. It retrieves the points from Python before passing them to
     * {@link ImageDataObject} for initialization and saving.
     *
     * @return true if method succeeded or already initialized, or false it
     * something went wrong
     */
    public boolean inferImage() {
        if (current.isInitialized()) {
            return true;
        }

        LoadingDialog loading = new LoadingDialog(parentFrame, current.getImgPath());
        loading.setLocationRelativeTo(parentFrame);

        ArrayList<PcdPoint> pointlist = loading.showDialog();

        current.initialize(pointlist, typeIdentifierList, typeIconList, typeConfigList);
        boolean result = current.isInitialized();
        String utfa = bundle.getString("ImageDataStorage.utfa");
        String ERR = bundle.getString("ImageDataStorage.err");
        if (!result) {
            JOptionPane.showMessageDialog(parentFrame, utfa, ERR, JOptionPane.ERROR_MESSAGE);
        }

        return result;
    }

    /**
     * Initializes a specific index of {@link ImageDataObject} in
     * {@link ImageDataStorage#imageList} with passed points.
     *
     * @param i index to initialize
     * @param pointlist the list of {@link PcdPoint} to initialize it with
     * @return true if the image is initialized
     */
    public boolean initImage(int i, ArrayList<PcdPoint> pointlist) {
        imageList.get(i).initialize(pointlist, typeIdentifierList, typeIconList, typeConfigList);
        return imageList.get(i).isInitialized();
    }

    /**
     * Submit image object indexes for inference by Python.
     *
     * @param idxList the indexes of objects in
     * {@link ImageDataStorage#imageList}
     */
    public void inferImages(ArrayList<Integer> idxList) {
        if (idxList.isEmpty()) {
            return;
        }

        LOGGER.info("Marked images found, submitting for inference: " + idxList.size());

        LoadingMultipleDialogGUI inferGui = new LoadingMultipleDialogGUI(parentFrame, idxList, getImagePathList());
        inferGui.setLocationRelativeTo(parentFrame);
        ArrayList<ArrayList<PcdPoint>> pointlistList = inferGui.showDialog();

        LOGGER.info("Starting post processing..");

        for (int i = 0; i < pointlistList.size(); i++) {
            LOGGER.info("Progress: " + (i + 1) + "/" + pointlistList.size());
            initImage(idxList.get(i), pointlistList.get(i));
        }
        
        parentFrame.resetSelection();
        parentFrame.loadTables();
    }

    /**
     * Clears the {@link ImageDataStorage#imageList}. This does not update
     * cache, so any malicious intent can be easily reversed with the press of a
     * single button.
     *
     * @see MainFrame#restoreItemActionPerformed
     */
    public void clear() {
        imageList.clear();
    }

    /**
     * Sets a point to a new type based on type name.
     *
     * @param p the {@link PcdPoint to be modified}
     * @param string the type name as found in
     * {@link ImageDataStorage#typeConfigList}
     */
    public void setPointType(PcdPoint p, String string) {
        int id = getPointIdentifier(string);
        if (id == -1) {
            return;
        }

        p.setType(id);
        p.setTypeName(string);
    }

    /**
     * Load the image object list from a project file. Safe-proofed against
     * malicious intent.
     *
     * @see FileUtils#loadProject(java.io.File)
     * @see ImageDataStorage#setImageObjectList(java.util.ArrayList)
     * @param file the project file to load
     */
    public void loadProject(File file) {
        ArrayList<ImageDataObject> objList = FileUtils.loadProject(file);

        if (objList == null) {
            return;
        }

        objList.forEach(imageDataObject -> imageDataObject.initializeOverlay(typeIdentifierList, typeIconList));

        setImageObjectList(objList);
    }

    /**
     * Grabs a list of all image paths in order
     * @return the list of image object associated image paths
     */
    private List<String> getImagePathList() {
        return imageList.stream().map(imgObj -> imgObj.getImgPath()).collect(Collectors.toList());
    }
}
