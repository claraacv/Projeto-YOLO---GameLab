package com.example.tomatodetector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class YoloDetector {

    private static final int INPUT_SIZE = 640;
    private static final float CONFIDENCE_THRESHOLD = 0.5f; // Ajuste se quiser mais/menos sensível

    private Interpreter interpreter;
    private ImageProcessor imageProcessor;
    private int numClasses = 0;
    private static final String TAG = "YoloDetector";

    public YoloDetector(Context context) {
        try {
            ByteBuffer model = FileUtil.loadMappedFile(context, "teste11yolo.tflite"); // coloque o nome certo do seu modelo
            interpreter = new Interpreter(model);

            // Detecta quantas classes o modelo tem
            int[] outputShape = interpreter.getOutputTensor(0).shape(); // ex.: [1, 85, 8400] -> 85 - 5 = 80 classes
            numClasses = outputShape[1] - 5;
            Log.d(TAG, "Modelo carregado com " + numClasses + " classes.");

            imageProcessor = new ImageProcessor.Builder()
                    .add(new ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<DetectionResult> detect(ImageProxy imageProxy) {
        List<DetectionResult> results = new ArrayList<>();
        try {
            Bitmap bitmap = imageProxyToBitmap(imageProxy);
            if (bitmap == null) return results;

            int imageWidth = bitmap.getWidth();
            int imageHeight = bitmap.getHeight();

            TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
            tensorImage.load(bitmap);
            tensorImage = imageProcessor.process(tensorImage);

            int totalOutputs = 5 + numClasses;
            float[][][] output = new float[1][totalOutputs][8400];
            interpreter.run(tensorImage.getBuffer(), output);

            // Transpõe para [8400][totalOutputs]
            float[][] transposedOutput = new float[8400][totalOutputs];
            for (int i = 0; i < totalOutputs; i++) {
                for (int j = 0; j < 8400; j++) {
                    transposedOutput[j][i] = output[0][i][j];
                }
            }

            for (int i = 0; i < 8400; i++) {
                float x = transposedOutput[i][0];
                float y = transposedOutput[i][1];
                float w = transposedOutput[i][2];
                float h = transposedOutput[i][3];
                float confidence = transposedOutput[i][4];

                if (confidence > CONFIDENCE_THRESHOLD) {
                    // Descobre classe com maior probabilidade
                    int classId = -1;
                    float maxClassProb = 0f;
                    for (int c = 0; c < numClasses; c++) {
                        float classProb = transposedOutput[i][5 + c];
                        if (classProb > maxClassProb) {
                            maxClassProb = classProb;
                            classId = c;
                        }
                    }

                    if (classId >= 0) {
                        float left = (x - w / 2f) * imageWidth;
                        float top = (y - h / 2f) * imageHeight;
                        float right = (x + w / 2f) * imageWidth;
                        float bottom = (y + h / 2f) * imageHeight;

                        Log.d(TAG, "Objeto detectado (classe " + classId + ") conf=" + confidence);

                        RectF box = new RectF(left, top, right, bottom);
                        results.add(new DetectionResult(box, "Classe_" + classId, confidence));
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            imageProxy.close();
        }
        return results;
    }

    public static Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);

        byte[] uBytes = new byte[uSize];
        byte[] vBytes = new byte[vSize];
        uBuffer.get(uBytes);
        vBuffer.get(vBytes);
        for (int i = 0; i < uSize; i++) {
            nv21[ySize + i * 2] = vBytes[i];
            nv21[ySize + i * 2 + 1] = uBytes[i];
        }

        YuvImage yuvImage = new YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.getWidth(),
                imageProxy.getHeight(),
                null
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
        }
    }
}
