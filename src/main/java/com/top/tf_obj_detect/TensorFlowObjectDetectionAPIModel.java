package com.top.tf_obj_detect;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;
import java.util.PriorityQueue;

//import android.graphics.Bitmap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.Graph;
import org.tensorflow.Operation;
import org.tensorflow.Tensor;

import com.newsplore.inception.service.TensorFlowInferenceInterface;

public class TensorFlowObjectDetectionAPIModel implements Classifier{
	private static final Logger LOGGER = LoggerFactory.getLogger(TensorFlowObjectDetectionAPIModel.class);

	  // Only return this many results.
	  private static final int MAX_RESULTS = 100;

	  // Config values.
	  private String inputName;
	  private int inputSize;
	  private int inputWidth ;
	  private int inputHeight;

	  // Pre-allocated buffers.
	  private Vector<String> labels = new Vector<String>();
	  private int[] intValues;
	  private byte[] byteValues;
	  private float[] outputLocations;
	  private float[] outputScores;
	  private float[] outputClasses;
	  private float[] outputNumDetections;
	  private String[] outputNames;

	  private boolean logStats = false;

	  private TensorFlowInferenceInterface inferenceInterface;

	  /**
	   * Initializes a native TensorFlow session for classifying images.
	   *
	   * @param assetManager The asset manager to be used to load assets.
	   * @param modelFilename The filepath of the model GraphDef protocol buffer.
	   * @param labelFilename The filepath of label file for classes.
	   */
	  public static Classifier create(

	    final String modelFilename,
	    final String labelFilename,
	    final int inputWidth,final int inputHeight) throws IOException {
	    final TensorFlowObjectDetectionAPIModel d = new TensorFlowObjectDetectionAPIModel();

	    InputStream labelsInput = null;
	    labelsInput = new FileInputStream(labelFilename);
	    BufferedReader br = null;
	    br = new BufferedReader(new InputStreamReader(labelsInput));
	    String line;
	    while ((line = br.readLine()) != null) {
	      LOGGER.info(line);
	      d.labels.add(line);
	    }
	    br.close();

	    d.inferenceInterface = new TensorFlowInferenceInterface(modelFilename);
	    final Graph g = d.inferenceInterface.graph();
	    d.inputName = "image_tensor";
	    // The inputName node has a shape of [N, H, W, C], where
	    // N is the batch size
	    // H = W are the height and width
	    // C is the number of channels (3 for our purposes - RGB)
	    final Operation inputOp = g.operation(d.inputName);
	    if (inputOp == null) {
	      throw new RuntimeException("Failed to find input Node '" + d.inputName + "'");
	    }
	    d.inputWidth = inputWidth;
	    d.inputHeight = inputHeight;
	
	    // The outputScoresName node has a shape of [N, NumLocations], where N
	    // is the batch size.
	    final Operation outputOp1 = g.operation("detection_scores");
	    if (outputOp1 == null) {
	      throw new RuntimeException("Failed to find output Node 'detection_scores'");
	    }
	    final Operation outputOp2 = g.operation("detection_boxes");
	    if (outputOp2 == null) {
	      throw new RuntimeException("Failed to find output Node 'detection_boxes'");
	    }
	    final Operation outputOp3 = g.operation("detection_classes");
	    if (outputOp3 == null) {
	      throw new RuntimeException("Failed to find output Node 'detection_classes'");
	    }

	    // Pre-allocate buffers.
	    d.outputNames = new String[] {"detection_boxes", "detection_scores",
	                                  "detection_classes", "num_detections"};
	    d.intValues = new int[d.inputWidth * d.inputHeight];
	    d.byteValues = new byte[d.inputWidth * d.inputHeight * 3];
	    d.outputScores = new float[MAX_RESULTS];
	    d.outputLocations = new float[MAX_RESULTS * 4];
	    d.outputClasses = new float[MAX_RESULTS];
	    d.outputNumDetections = new float[1];
	    return d;
	  }

	  public TensorFlowObjectDetectionAPIModel() {}

	  @Override
	  public List<Recognition> recognizeImage(byte[] imageByte) {
	    // Log this method so that it can be analyzed with systrace.
		  LOGGER.info("recognizeImage");

		  LOGGER.info("preprocessBitmap");
	    // Preprocess the image data from 0-255 int to normalized float based
	    // on the provided parameters.
		
	    //bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

//	    for (int i = 0; i < intValues.length; ++i) {
//	      byteValues[i * 3 + 2] = (byte) (intValues[i] & 0xFF);
//	      byteValues[i * 3 + 1] = (byte) ((intValues[i] >> 8) & 0xFF);
//	      byteValues[i * 3 + 0] = (byte) ((intValues[i] >> 16) & 0xFF);
//    }
    // Copy the input data into TensorFlow.
	    LOGGER.info("feed");
	  /***
	   * NHWC is the TensorFlow default and NCHW is the optimal format to use when training on NVIDIA GPUs using cuDNN.
	   * 
	   * added by xiatao
	   */
	    inferenceInterface.feed(inputName, imageByte, 1,inputHeight,inputWidth ,3);   
	    // Run the inference call.
	    inferenceInterface.run(outputNames, logStats);

	    // Copy the output Tensor back into the output array.
	    LOGGER.info("fetch");
	    outputLocations = new float[MAX_RESULTS * 4];
	    outputScores = new float[MAX_RESULTS];
	    outputClasses = new float[MAX_RESULTS];
	    outputNumDetections = new float[1];
	    inferenceInterface.fetch(outputNames[0], outputLocations);
	    inferenceInterface.fetch(outputNames[1], outputScores);
	    inferenceInterface.fetch(outputNames[2], outputClasses);
	    inferenceInterface.fetch(outputNames[3], outputNumDetections);
	
   
	    // Find the best detections. all files
	    final PriorityQueue<Recognition> pq =
	        new PriorityQueue<Recognition>(
	            1,
	            new Comparator<Recognition>() {
	              @Override
	              public int compare(final Recognition lhs, final Recognition rhs) {
	                // Intentionally reversed to put high confidence at the head of the queue.
	                return Float.compare(rhs.getConfidence(), lhs.getConfidence());
	              }
	            });
	    // Scale them back to the input size.
	    for (int i = 0; i < outputScores.length; ++i) {
	      final RectF detection =
	    		  /**
	    		   * add by xiatao  to process image whose width not equal to height. the corresponding python code is below:
	    		   *   ## cautious about the sequence y is in front of x
	    		   * ymin, xmin, ymax, xmax = boxes[0][i][0]*img_height,boxes[0][i][1]*img_width,boxes[0][i][2]*img_height,boxes[0][i][3]*img_width
	    		   */
	    		  new RectF(
	    	              outputLocations[4 * i + 1] *inputHeight,
	    	              outputLocations[4 * i] *  inputWidth,
	    	              outputLocations[4 * i + 3] * inputHeight,
	    	              outputLocations[4 * i + 2] * inputWidth);
//	          new RectF(
//	              outputLocations[4 * i + 1] * inputSize,
//	              outputLocations[4 * i] * inputSize,
//	              outputLocations[4 * i + 3] * inputSize,
//	              outputLocations[4 * i + 2] * inputSize);
	      if(outputClasses[i]!=0)
	      {
	    	  pq.add(new Recognition("" + i, labels.get((int) outputClasses[i]-1), outputScores[i], detection));
	      }
//	      pq.add(new Recognition("" + i, labels.get((int) outputClasses[i]), outputScores[i], detection));
	      
	      
	    }

	    final ArrayList<Recognition> recognitions = new ArrayList<Recognition>();
	    for (int i = 0; i < Math.min(pq.size(), MAX_RESULTS); ++i) {
	      recognitions.add(pq.poll());
	    }
	  
	    return recognitions;
	  }

	  @Override
	  public void enableStatLogging(final boolean logStats) {
	    this.logStats = logStats;
	  }

	  @Override
	  public String getStatString() {
	    return inferenceInterface.getStatString();
	  }

	  @Override
	  public void close() {
	    inferenceInterface.close();
	  }
}
