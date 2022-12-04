package pcd.data;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pcd.utils.AngleWrapper;
import pcd.utils.Constant;
import pcd.utils.PointUtils;

/**
 * @author Tibor Sloboda
 *
 * Stores data pertaining to images and points on these images, denoting the
 * cilia coordinates within it.
 *
 * @see PcdPoint
 * @see ImageDataStorage
 */
public class ImageDataObject implements Serializable {

    private static final Logger LOGGER = LogManager.getLogger(ImageDataObject.class);

    /**
     * The expected common image width of PCD diagnosis slides
     */
    private final static int WIDTH = 3406;
    /**
     * The expected common image height of PCD diagnosis slides
     */
    private final static int HEIGHT = 2672;

    /**
     * A list of {@link PcdPoint}
     *
     * @see PcdPoint
     */
    private ArrayList<PcdPoint> pointList;
    /**
     * An absolute {@link String} path to associated image file
     */
    private String imgPath;
    /**
     * A {@link Graphics2D} based overlay to draw points over the image.
     *
     * @see pcd.imageviewer.Overlay
     *It also uses the OverlayComponent which is  <code>JComponent</code> implementing an overlay.
     */
    private PointOverlay layer = null;
    /**
     * Determines whether the image object has been initialized with points
     *
     * @see ImageDataObject#pointList
     * @see ImageDataObject#initialize
     */
    private boolean initialized = false;
    /**
     * Determined whether the {@link ImageDataObject#pointList} has been
     * initialized with angles
     *
     * @see ImageDataObject#angleInitialize
     */
    private boolean angleInitialized = false;
    /**
     * The normalized average of all points of the image object. The normalized
     * angle is the average of all angles shifted to a 0-180 degree range.
     */
    private double avgAngle = -1.;
    /**
     * Standard deviations of all {@link ImageDataObject#pointList} point angles
     * from average. Angles of points must be normalized before calculating
     * deviation
     *
     * @see ImageDataObject#avgAngle
     */
    private double stdAngle = -1.;

    /**
     * @param path Absolute path to the image files
     */
    public ImageDataObject(String path) {
        imgPath = path;
    }

    /**
     * A copy constructor
     *
     * @param obj {@link ImageDataObject} instantiated object
     */
    public ImageDataObject(ImageDataObject obj) {
        this.pointList = obj.pointList;
        this.imgPath = obj.getImgPath();
        this.initialized = obj.initialized;
        this.layer = obj.getOverlay();
        this.stdAngle = obj.getStdAngle();
        this.avgAngle = obj.getAvgAngle();
        this.angleInitialized = obj.isAngleInitialized();
    }

    /**
     * Checks if angles have been initialized
     *
     * @return True if initialized
     */
    public boolean isAngleInitialized() {
        return angleInitialized;
    }

    /**
     * Initializes the angle properties of the image based on the data. If count
     * is 0 or if angles are already initialized, it ends, otherwise it sets
     * {@link ImageDataObject#angleInitialized} to true.
     *
     * This is mostly just a helper function and the arguments are passed to
     * {@link ImageDataObject#setAvgStdAngle(double, int)} for processing.
     *
     * @see ImageDataStorage#initializeAngles()
     *
     * @param avg The average of normalized cilia angles
     * @param count The number of non-negative cilia angles
     */
    public void angleInitialize(double avg, int count) {
        if (isAngleInitialized() || count == 0) {
            return;
        }

        setAvgStdAngle(avg, count);
        angleInitialized = true;
    }

    /**
     * @param val New boolean to set {@link ImageDataObject#angleInitialized} to
     */
    public void setAngleInitialized(boolean val) {
        angleInitialized = val;
    }

    /**
     *
     * @return The value of {@link ImageDataObject#avgAngle}
     */
    public double getAvgAngle() {
        return avgAngle;
    }

    /**
     *
     * @return The value of {@link ImageDataObject#stdAngle}
     */
    public double getStdAngle() {
        return stdAngle;
    }

    /**
     *
     * @param avgAngle Double parameter to update
     * {@link ImageDataObject#avgAngle} to
     */
    public void setAvgAngle(double avgAngle) {
        this.avgAngle = avgAngle;
    }

    /**
     *
     * @param stdAngle Double parameter to update
     * {@link ImageDataObject#stdAngle} to
     */
    public void setStdAngle(double stdAngle) {
        this.stdAngle = stdAngle;
    }

    /**
     * This method is used to update the standard deviation of angles based on
     * normalized angles and the average of normalized angles.
     *
     * @param avgAngle The normalized average of angles
     * @param positiveCount The number of non-negative angles
     */
    public void setAvgStdAngle(double avgAngle, int positiveCount) {
        setAvgAngle(avgAngle);
        double sum = pointList
                .parallelStream()
                .filter(point -> point.getType() == 0 && point.getAngle() >= 0)
                .mapToDouble(point -> Math.pow((point.isAnglePositive() ? point.getAngle() + 90 : 90 - point.getAngle()) - avgAngle, 2) / (positiveCount - 1))
                .sum();

        if (positiveCount == 1) {
            setStdAngle(0.);
            return;
        }

        setStdAngle(Math.sqrt(sum));
    }

    /**
     * Updates the average of the statistical sample of the image object. It is
     * complementary to {@link ImageDataObject#setAvgStdAngle(double, int)} in
     * that it calculates the number of positive angles and the normalized
     * average as well. This is done using existing points so it only works when
     * initialized.
     * <p>
     * The standard deviation is then calculated using these in {@link ImageDataObject#setAvgStdAngle(double, int)}
     */
    public void updateAvgStdAngle() {
        if (!isInitialized()) {
            return;
        }
        
        angleInitialized = true;

        List<PcdPoint> pointListFiltered = pointList
                .parallelStream()
                .filter(point -> point.getType() == 0)
                .filter(point -> point.getAngle() >= 0)
                .collect(Collectors.toList());

        int count = pointListFiltered.size();
        OptionalDouble optangle = pointListFiltered
                .parallelStream()
                .mapToDouble(point -> point.isAnglePositive() ? point.getAngle() + 90 : 90 - point.getAngle())
                .average();

        if (optangle.isPresent()) {
            setAvgStdAngle(optangle.getAsDouble(), count);
        } else {
            setAvgAngle(0);
            setStdAngle(0);
        }
    }

    /**
     * Initializes the {@link ImageDataObject} with points from the Python
     * process. It filters the points using the point filtering utility before
     * saving them to {@link ImageDataObject#pointList} .. It also instantiates
     * {@link PointOverlay} using the {@link ImageDataObject#initializeOverlay}
     * method which is then used to draw the points over the image.
     * <p>
     * Prevents initialization if the size of the point list exceeds theoretical
     * maximum of around 4876 based on Python filtering mechanism to prevent
     * exploitation and hanging of the software
     *
     * @param pointlist An {@link ArrayList} of {@link PcdPoint}
     * @param typeIdentifierList Contains the cilia type IDs
     * @param typeIconList Optional list of paths to icon to be used to draw
     * points
     * @param typeConfigList List of type names for the cilia
     */
    public void initialize(ArrayList<PcdPoint> pointlist, ArrayList<Integer> typeIdentifierList, ArrayList<String> typeIconList, ArrayList<String> typeConfigList) {
        if (initialized || pointlist == null) {
            return;
        }

        if (pointlist.size() >= 4876) {
            return;
        }

        pointList = pointlist;

        PointUtils.removeClosestPointsSimple(pointList, Constant.FILTER_THRESHOLD_DISTANCE);

        pointList.forEach(pcdPoint -> pcdPoint.setTypeName(typeConfigList.get(typeIdentifierList.indexOf(pcdPoint.getType()))));

        initialized = true;
        initializeOverlay(typeIdentifierList, typeIconList);

        LOGGER.info("Image object initialization finished");
    }

    /**
     * Initializes the PointOverlay to draw points on the image.
     *
     * @param typeIdentifierList The list of cilia type IDs
     * @param typeIconList Optional (can be empty) list of paths to icon to be
     * used as points
     */
    public void initializeOverlay(ArrayList<Integer> typeIdentifierList, ArrayList<String> typeIconList) {
        if (pointList != null && pointList.size() <= 400) {
            layer = new PointOverlay(pointList, typeIconList, typeIdentifierList);
            initialized = true;
        }
    }

    /**
     * Loads the associated image, and resizes it if needed.
     *
     * @return The {@link BufferedImage} or null if failed to load
     */
    public BufferedImage loadImage() {

        try {
            BufferedImage img = ImageIO.read(new File(imgPath));
            if (img.getWidth() != WIDTH || img.getHeight() != HEIGHT) {
                return resizeImage(img);
            } else {
                return img;
            }
        } catch (IOException e) {
            String imageLoadFail = "Image loading failed!";
            LOGGER.error(imageLoadFail, e);
            return null;
        }
    }

    /**
     * Returns the resized {@link BufferedImage} to match typical PCD slide
     * resolution.
     *
     * @param originalImage The {@link BufferedImage} to be resized, which must
     * be grayscale.
     * @return A resized {@link BufferedImage} matching the
     * {@link ImageDataObject#WIDTH} and {@link ImageDataObject#HEIGHT}
     * dimensions.
     * @throws IOException when resizing fails
     */
    private BufferedImage resizeImage(BufferedImage originalImage) throws IOException {
        Image resultingImage = originalImage.getScaledInstance(WIDTH, HEIGHT, Image.SCALE_DEFAULT);
        BufferedImage outputImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
        outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
        return outputImage;
    }

    /**
     * Returns instantiated drawing layer for points or null if not instantiated
     *
     * @return The instantiated {@link ImageDataObject#layer} or null
     */
    public PointOverlay getOverlay() {
        return layer;
    }

    /**
     * Checks if passed file is the same file as the image associated with this
     * object.
     *
     * @param path A {@link String} path to the file
     * @return True if the files match
     * @throws IOException when the software doesn't have access privileges to
     * check if files match
     */
    public boolean fileMatch(String path) throws IOException {
        try {
            return Files.isSameFile(Paths.get(path), Paths.get(imgPath));
        } catch (IOException e) {
            String fileCompareFail = "File compare failed!";
            LOGGER.error(fileCompareFail, e);
            throw e;
        }
    }

    /**
     * Find the closest point to a coordinate
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @return the closest point from {@link ImageDataObject#pointList} or null
     */
    public PcdPoint getClosestPoint(int x, int y) {
        if (pointList == null) {
            return null;
        }

        return PointUtils.getSimpleClosestPoint(x, y, pointList);
    }

    /**
     *
     * @return True if image object is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Updates the opacity value of {@link ImageDataObject#layer} and re-draws
     * it.
     *
     * @param f The opacity, between 0 and 1 (corresponds to 0-100%)
     */
    public void setPointsOpacity(float f) {
        if(layer != null){
            layer.setOpacity(f);
            layer.repaint();
        }
    }

    /**
     * Adds a new {@link PcdPoint} to the list of points for the image object.
     * Prevents adding a new point if the current amount exceeds 400. Also
     * redraws {@link ImageDataObject#layer} once added to show newly added
     * point.
     *
     * @param pcdPoint The point to be added
     */
    protected void addPoint(PcdPoint pcdPoint) {
        if (pointList == null || pointList.size() >= 400) {
            return;
        }

        pointList.add(pcdPoint);
        layer.repaint();
    }

    /**
     * Removes a point from the image object, if found. Then repaints
     * {@link ImageDataObject#layer}
     *
     * @param p the {@link PcdPoint} to be removed
     */
    void remPoint(PcdPoint p) {
        if (pointList == null) {
            return;
        }

        pointList.remove(p);
        layer.repaint();
    }

    /**
     * Replaces the current list of points with a new one. Doesn't do anything
     * if passed list exceeds a size of 400.
     *
     * @param points An {@link ArrayList} of {@link PcdPoint}
     */
    public void setPointList(ArrayList<PcdPoint> points) {
        if (points == null || points.size() > 400) {
            return;
        }

        this.pointList = points;
    }

    /**
     * Gets the name of the image based on the path to the file
     *
     * @return name of the image file
     */
    public String getImageName() {
        return Paths.get(imgPath).getFileName().toString();
    }

    /**
     * Returns the path to the image associated with the image object
     *
     * @return a {@link String} path to the image
     */
    public String getImgPath() {
        if (imgPath == null) {
            return "";
        }
        return imgPath;
    }

    /**
     * Retrieves offsets and angles from {@link AngleWrapper} and updates all
     * {@link PcdPoint} in {@link ImageDataObject#pointList} with the values.
     *
     * It also calculates the average angle and passes it to initialized
     * together with the count of class 0 cilia into std angle init.
     *
     * @param wrapper The passed {@link AngleWrapper} containing values to
     * update points.
     */
    void mapAngles(AngleWrapper wrapper) {
        for (int i = 0; i < pointList.size(); i++) {
            pointList.get(i).setAngle(wrapper.getAngles().get(i));
            pointList.get(i).setAnglePositive(wrapper.getPositivenessBools().get(i));
            pointList.get(i).x += wrapper.getXoffset().get(i);
            pointList.get(i).y += wrapper.getYoffset().get(i);
            pointList.get(i).setOrigAngle(wrapper.getAngles().get(i));
            pointList.get(i).setOrigAnglePositive(wrapper.getPositivenessBools().get(i));
        }

        List<PcdPoint> pointListFiltered = pointList
                .parallelStream()
                .filter(point -> point.getType() == 0)
                .filter(point -> point.getAngle() >= 0)
                .collect(Collectors.toList());

        int count = pointListFiltered.size();
        OptionalDouble optangle = pointListFiltered
                .parallelStream()
                .mapToDouble(point -> point.isAnglePositive() ? point.getAngle() + 90 : 90 - point.getAngle())
                .average();

        if (optangle.isPresent()) {
            angleInitialize(optangle.getAsDouble(), count);
        } else {
            setAvgAngle(0);
            setStdAngle(0);
        }

    }

    /**
     * Returns the index-bound list of the IDs of cilia types associated to
     * points
     *
     * @return a {@link ArrayList} of {@link Integer} of the IDs of
     * {@link PcdPoint} in {@link ImageDataObject#pointList}
     */
    public ArrayList<Integer> getPointTypes() {
        ArrayList<Integer> typeList = new ArrayList<>();
        pointList.forEach(pcdPoint -> typeList.add(pcdPoint.getType()));
        return typeList;
    }

    /**
     * Returns the list of points.
     * <p>
     * This breaks encapsulation but data contained inside
     * {@link ImageDataObject#pointList} is not sensitive and corruption of this
     * data will not harm the software.
     * <p>
     * It is additionally overflow protected by checking the size of the list,
     * so that the application doesn't freeze attempting to draw and load all
     * points into tables.
     *
     * @return A reference to {@link ImageDataObject#pointList} or null if too
     * many points present
     */
    public final List<PcdPoint> getPointList() {
        if (pointList == null || pointList.size() >= 400) {
            return null;
        }

        return pointList;
    }

    /**
     * Updates the associated image path and loads it.
     *
     * @param imgPath A {@link String} path to the image
     * @return The loaded {@link BufferedImage}
     */
    protected BufferedImage loadImage(String imgPath) {
        this.imgPath = imgPath;
        return loadImage();
    }

    /**
     * Returns a cloned reference to {@link ImageDataObject#pointList} cast to
     * {@link Point}.
     *
     * @return an {@link ArrayList} of {@link Point}
     */
    ArrayList<Point> getRawPointList() {
        return (ArrayList<Point>) pointList.clone();
    }
}
