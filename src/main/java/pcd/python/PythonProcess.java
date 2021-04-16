package pcd.python;

// Python process not yet implemented for testing purposes
import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import pcd.data.ImageDataStorage;
import pcd.data.PcdPoint;
import pcd.utils.Constant;

public class PythonProcess {

    private TCPServer server;
    private ProcessBuilder pb = null;
    private final boolean process_debug;
    private final boolean server_debug;

    public PythonProcess() {
        this.process_debug = Constant.PROCESS_DEBUG;
        this.server_debug = Constant.SERVER_DEBUG;
        
        if (!process_debug) {
            initProcess();
        }
        
        if (!server_debug){
            server = new TCPServer(Constant.SERVER_PORT, pb);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop();
        }
    }

    private void initProcess() {
        pb = new ProcessBuilder("python/main.exe").inheritIO();
        pb.directory(new File(System.getProperty("user.dir") + "/python"));
    }
    
    synchronized private ArrayList<Double> _getAngles_debug(ArrayList<Point> pointList){
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            ImageDataStorage.getLOGGER().error("Thread interrupted", ex);
        }
        ArrayList<Double> angles = new ArrayList<>();
        
        pointList.stream().map(_item -> ThreadLocalRandom.current().nextDouble() * 28).forEachOrdered(angle -> {
            angles.add(angle);
        });
        
        return angles;
    }
    
    strictfp synchronized private ArrayList<Double> _getAngles(String imgPath, ArrayList<Point> pointList) throws IOException {
        String t;
        String pointString = "";
        
        for (Point pcdPoint : pointList) {
            pointString += ";";
            pointString += String.valueOf(pcdPoint.x) + "," + String.valueOf(pcdPoint.y);
        }
        
        try {
            server.send(Constant.ANGLE_SERVER_STRING + imgPath + pointString);
            t = server.receive();
        } catch (IOException e) {
            System.out.println("Failed");
            ImageDataStorage.getLOGGER().error("Getting angles failed!", e);
            throw e;
        }
        
        String[] angleString = t.split(";");
        ArrayList<Double> angles = new ArrayList<>();
        
        for (String string : angleString) {
            angles.add(Double.parseDouble(string));
        }
        
        return angles;
    }
    
    synchronized public ArrayList<Double> getAngles(String imgPath, ArrayList<Point> pointList) throws IOException {
        if(server_debug)
            return _getAngles_debug(pointList);
        else
            return _getAngles(imgPath, pointList);
    }

    synchronized public ArrayList<PcdPoint> getPoints(String imgPath, javax.swing.JProgressBar progressBar, int count) throws IOException {
        int progress = progressBar.getValue();
        int max = progressBar.getMaximum();
        int increment = max / (count + 1);
        progressBar.setValue(progress + increment);
        
        return getPoints(imgPath);
    }

    synchronized public ArrayList<PcdPoint> getPoints(String imgPath) throws IOException {
        if (server_debug) {
            return _getPoints_debug();
        } else {
            return _getPoints(imgPath);
        }
    }

    synchronized private ArrayList<PcdPoint> _getPoints(String imgPath) throws IOException {
        String t;
        ArrayList<PcdPoint> pointList = new ArrayList<>();

        try {
            server.send(Constant.INFERENCE_SERVER_STRING + imgPath);
            t = server.receive();
        } catch (IOException e) {
            System.out.println("Failed");
            ImageDataStorage.getLOGGER().error("Getting points failed!", e);
            throw e;
        }

        String[] points = t.split(";");

        for (String point : points) {
            PcdPoint point1 = new PcdPoint();
            String[] data = point.split(",");
            point1.setType(Short.parseShort(data[2]));
            point1.setX(Integer.parseInt(data[0]));
            point1.setY(Integer.parseInt(data[1]));
            point1.setScore(Double.parseDouble(data[3]));
            pointList.add(point1);
        }
        
        if(Constant.DEBUG_MSG)
            System.out.println("Points made, returning");

        return pointList;
    }

    synchronized private ArrayList<PcdPoint> _getPoints_debug() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ex) {
            ImageDataStorage.getLOGGER().error("Thread interrupted", ex);
        }
        ArrayList<PcdPoint> debugPoints = new ArrayList<>();

        for (int i = 0; i < 300; i++) {
            int randtype = ThreadLocalRandom.current().nextInt(0, 2 + 1);
            int randx = ThreadLocalRandom.current().nextInt(100, 3000 + 1);
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
