package pcd.python;

import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pcd.data.PcdPoint;
import pcd.utils.AngleWrapper;
import pcd.utils.Constant;

/**
 * 
 * @author Tibor Sloboda
 * 
 * The class representing and keeping a reference to the running Python process.
 * Only one instance is allowed at a time. This class is a singleton.
 */

public final class PythonProcess {
    private static final Logger LOGGER = LogManager.getLogger(PythonProcess.class);

    private static PythonProcess pyproc = null;
    private TCPServer server;
    private ProcessBuilder pb = null;
    private final boolean server_debug;
    private boolean skip_detection = false;

    private PythonProcess() {
        this.server_debug = Constant.SERVER_DEBUG;

        if (!Constant.PROCESS_DEBUG) {
            initProcess();
        }

        if (!server_debug) {
            server = new TCPServer(Constant.SERVER_PORT, pb);
        }
    }

    public void activateDetection() {
        this.skip_detection = false;
    }
    
    public void deactivateDetection() {
        this.skip_detection = true;
    }

    /**
     * Retrieves the Python process instance, or creates if it doesn't exist yet
     * @return an instance of this class
     */
    public static PythonProcess getInstance(){
        if(pyproc == null){
            pyproc = new PythonProcess();
            return pyproc;
        }
        
        return pyproc;
    }

    /**
     * Stops the python process and server
     */
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }

    /**
     * Initializes the python process
     */
    private void initProcess() {
        pb = new ProcessBuilder("python/main.exe").inheritIO();
        pb.directory(new File(System.getProperty("user.dir") + "/python"));
    }

    /**
     * Pythonless faker version of {@link PythonProcess#_getAngles(java.lang.String, java.util.ArrayList)} 
     * @param pointList the points for which to generate angles
     * @return Angle wrapped offsets and angles
     */
    private AngleWrapper _getAngles_debug(ArrayList<Point> pointList) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            LOGGER.error("Thread interrupted", ex);
        }
        ArrayList<Double> angles = new ArrayList<>();
        ArrayList<Boolean> positiveness = new ArrayList<>();
        ArrayList<Integer> yoffsets = new ArrayList<>();
        ArrayList<Integer> xoffsets = new ArrayList<>();

        for (int i = 0; i < pointList.size(); i++) {
            angles.add(ThreadLocalRandom.current().nextDouble() * 28);
            positiveness.add(ThreadLocalRandom.current().nextBoolean());
            yoffsets.add(ThreadLocalRandom.current().nextInt(-10, +10));
            xoffsets.add(ThreadLocalRandom.current().nextInt(-10, +10));
        }

        angles.set(0, -1.);

        return new AngleWrapper(angles, positiveness, xoffsets, yoffsets);
    }

    /**
     * Retrieves angles based on points from Python by parsing the string it returns.
     * @param pointList the points for which to generate angles
     * @param imgPath path of image
     * @return Angle wrapped offsets and angles
     * @throws IOException if cannot send or receive from the server
     */
    strictfp private AngleWrapper _getAngles(String imgPath, ArrayList<Point> pointList) throws IOException {
        String t;
        StringBuilder pointString = new StringBuilder();

        for (Point pcdPoint : pointList) {
            pointString.append(";");
            pointString.append(pcdPoint.x).append(",").append(pcdPoint.y);
        }

        try {
            server.send(Constant.ANGLE_SERVER_STRING + imgPath + pointString);
            t = server.receive();
        } catch (IOException e) {
            LOGGER.error("Getting angles failed!", e);
            throw e;
        }

        String[] angleString = t.split(";");
        ArrayList<Double> angles = new ArrayList<>();
        ArrayList<Boolean> positivenessBools = new ArrayList<>();
        ArrayList<Integer> yoffsets = new ArrayList<>();
        ArrayList<Integer> xoffsets = new ArrayList<>();

        for (String string : angleString) {
            try {
                String[] split = string.split(",");
                angles.add(Double.parseDouble(split[0]));
                positivenessBools.add("t".equals(split[1]));
                xoffsets.add(Integer.parseInt(split[2]));
                yoffsets.add(Integer.parseInt(split[3]));
            } catch (NumberFormatException e) {
                LOGGER.error("One of the parsed numbers had an invalid number format", e);
            }
        }

        return new AngleWrapper(angles, positivenessBools, xoffsets, yoffsets);
    }

    /**
     * Decides whether to simulate angle retrieval or whether to use python, based on debug constant
     * @param imgPath path to the image for which we are retrieving angles based on points
     * @param pointList the points which are used to construct the string passed to python
     * @return the wrapped angle and offset values
     * @see AngleWrapper
     * @throws IOException when a socket exception occurs
     */
    public AngleWrapper getAngles(String imgPath, ArrayList<Point> pointList) throws IOException {
        if (server_debug) {
            return _getAngles_debug(pointList);
        } else {
            return _getAngles(imgPath, pointList);
        }
    }

    /**
     * Decides whether to simulate pointw retrieval or whether to use python, based on debug constant
     * @param imgPath path to the image for which we are retrieving points
     * @return the list of found points and their types
     * @see PcdPoint
     * @throws IOException when a socket exception occurs
     */
    public ArrayList<PcdPoint> getPoints(String imgPath) throws IOException {
        if (server_debug) {
            return _getPoints_debug();
        } 
        if (skip_detection) {
            ArrayList<PcdPoint> debugPoints = new ArrayList<>();
            return debugPoints;
        }
        
        return _getPoints(imgPath);
    }

    /**
     * Retrieves points from Python by parsing the string it returns.
     * @param imgPath the path to the image on which python will do inference
     * @return the list of parsed detected points
     * @throws IOException if getting points fails
     */
 
    private ArrayList<PcdPoint> _getPoints(String imgPath) throws IOException {
        String t;
        ArrayList<PcdPoint> pointList = new ArrayList<>();

        try {
            server.send(Constant.INFERENCE_SERVER_STRING + imgPath);
            t = server.receive();
            LOGGER.info("Function returned in _getPoints");
        } catch (IOException e) {
            System.out.println("Failed");
            LOGGER.error("Getting points failed!", e);
            throw e;
        }

        LOGGER.info("Splitting points..");

        String[] points = t.split(";");

        LOGGER.info("Adding points..");

        for (String point : points) {
            try {
                PcdPoint point1 = new PcdPoint();
                String[] data = point.split(",");
                short classType = Short.parseShort(data[2]);
                if (Constant.ONLY_NORMAL && classType != 0) {
                    LOGGER.info(String.format("Point not used: class %d", classType));
                    continue;
                }
                point1.setType(classType);
                point1.setX(Integer.parseInt(data[0]));
                point1.setY(Integer.parseInt(data[1]));
                point1.setScore(Double.parseDouble(data[3]));
                pointList.add(point1);

                LOGGER.info(String.format("Point added: %d, %d, %d, %f%n", point1.x, point1.y, point1.getType(), point1.getScore()));
            } catch (NumberFormatException e) {
                LOGGER.error("Error during parsing of values from string", e);
            }

        }

        LOGGER.info("Points finished parsing");

        return pointList;
    }

    /**
     * Retrieves points by randomly generating their values, simulating Python
     * @return the list of generated
     */
    private ArrayList<PcdPoint> _getPoints_debug() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            LOGGER.error("Thread interrupted", ex);
        }
        ArrayList<PcdPoint> debugPoints = new ArrayList<>();

        for (int i = 0; i < 80; i++) {
            int randtype = ThreadLocalRandom.current().nextInt(0, 2 + 1);
            int randx = ThreadLocalRandom.current().nextInt(100, 3200 + 1);
            int randy = ThreadLocalRandom.current().nextInt(100, 2000 + 1);
            double rands = ThreadLocalRandom.current().nextDouble(0., 1.);
            PcdPoint p = new PcdPoint(randx, randy);
            p.setType((short) randtype);
            p.setScore(rands);
            debugPoints.add(p);
        }

        return debugPoints;
    }
}
