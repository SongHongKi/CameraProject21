package com.example.cameraproject;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionPoint;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceContour;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    ImageView imageView;
    String currentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btn_camera = findViewById(R.id.btn_camera);
        imageView = findViewById(R.id.cameraView);

        btn_camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Step1_CameraSetting();
            }
        });

    }

    private void Step1_CameraSetting(){
        int camera_allow = ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA);
        if (camera_allow == PackageManager.PERMISSION_GRANTED) {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                File photoFile = null;
                try {
                    photoFile = Step2_createImageFile();
                } catch (IOException ex) {
                }
                if (photoFile != null) {
                    Uri photoURI = FileProvider.getUriForFile(this, "com.example.cameraproject.fileprovider", photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    startActivityForResult(takePictureIntent, 1);
                }
            }
        } else {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, 1); //1:RGB, 2:Gray
        }

    }

    private File Step2_createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName,  ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        int targetW = imageView.getWidth();
        int targetH = imageView.getHeight();
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;

        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;
        int scaleFactor = Math.min(photoW/targetW, photoH/targetH);

        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;


        //imageView.setImageBitmap(bitmap); //2. w/o rotation
        //Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath, bmOptions);
        //imageView.setImageBitmap(bitmap); //2. w/o rotation
        try { // 3. w/ rotation
            switch (requestCode) {
                case 1: {
                    if (resultCode == RESULT_OK) {
                        Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath, bmOptions);
                        if (bitmap != null) {
                            ExifInterface ei = new ExifInterface(currentPhotoPath);
                            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_UNDEFINED);
                            Bitmap rotatedBitmap = null;
                            Matrix matrix = new Matrix();
                            switch (orientation) {
                                case ExifInterface.ORIENTATION_ROTATE_180:
                                    matrix.setRotate(180);
                                case ExifInterface.ORIENTATION_ROTATE_90:
                                    matrix.setRotate(90);
                                case ExifInterface.ORIENTATION_ROTATE_270:
                                    matrix.setRotate(270);
                            }
                            rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                            imageView.setImageBitmap(rotatedBitmap);
                            detect1_runDetector(rotatedBitmap);
                        }
                    }
                    break;
                }
            }
        } catch (Exception error) {
            error.printStackTrace();
        }

    }

    private void detect1_runDetector(final Bitmap rotatedBitmap) {
        FirebaseApp.initializeApp(this);
        FirebaseVisionFaceDetectorOptions options =
                new FirebaseVisionFaceDetectorOptions.Builder()
                        .setClassificationMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
                        .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                        .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                        .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
                        .setMinFaceSize(0.15f)
                        .enableTracking()
                        .build();
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(rotatedBitmap);
        FirebaseVisionFaceDetector detector = FirebaseVision.getInstance().getVisionFaceDetector(options);

        Task<List<FirebaseVisionFace>> task = detector.detectInImage(image);
        task.addOnSuccessListener( new OnSuccessListener<List<FirebaseVisionFace>>() {
            @Override
            public void onSuccess(List<FirebaseVisionFace> faces) {

                for (FirebaseVisionFace face : faces) {
                    Rect bounds = face.getBoundingBox();
                    Canvas canvas = new Canvas(rotatedBitmap);
                    Paint Pnt = new Paint();
                    Pnt.setAntiAlias(true);
                    Pnt.setStyle(Paint.Style.STROKE);
                    Pnt.setStrokeWidth(20F);
                    Pnt.setColor(Color.BLACK);
                    canvas.drawRect(bounds, Pnt);
                    imageView.setImageDrawable(new BitmapDrawable(getResources(), rotatedBitmap));

                    List<FirebaseVisionPoint> ALLPoints = face.getContour(FirebaseVisionFaceContour.ALL_POINTS).getPoints();
                    Log.d("face_is", String.valueOf(ALLPoints.size()));

                    if (ALLPoints.size() >130){
                        detect2_processFaceTracking(rotatedBitmap, ALLPoints);
                    }else {
                        Toast.makeText(MainActivity.this, "얼굴이 부정확합니다. 다시해주세요", Toast.LENGTH_LONG).show();
                    }
                }
            }
        })
                .addOnFailureListener( new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d("face_is ", "detect1_runDetector fail");
                    }
                });
    }


    private void detect2_processFaceTracking(Bitmap rotatedBitmap, List<FirebaseVisionPoint> ALLPoints){
        Canvas canvas = new Canvas(rotatedBitmap);
        Paint Pnt = new Paint();
        Pnt.setAntiAlias(true);
        Pnt.setStyle(Paint.Style.STROKE);
        Pnt.setStrokeWidth(10F);
        Pnt.setColor(Color.GREEN);

        Path path07 = new Path();//아랫입술
        path07.moveTo(ALLPoints.get(114).getX(), ALLPoints.get(114).getY());
        path07.lineTo(ALLPoints.get(113).getX(), ALLPoints.get(113).getY());
        path07.lineTo(ALLPoints.get(112).getX(), ALLPoints.get(112).getY());
        path07.lineTo(ALLPoints.get(111).getX(), ALLPoints.get(111).getY());
        path07.lineTo(ALLPoints.get(110).getX(), ALLPoints.get(110).getY());
        path07.lineTo(ALLPoints.get(119).getX(), ALLPoints.get(119).getY());
        path07.lineTo(ALLPoints.get(120).getX(), ALLPoints.get(120).getY());
        path07.lineTo(ALLPoints.get(121).getX(), ALLPoints.get(121).getY());
        path07.lineTo(ALLPoints.get(122).getX(), ALLPoints.get(122).getY());
        path07.lineTo(ALLPoints.get(123).getX(), ALLPoints.get(123).getY());
        path07.lineTo(ALLPoints.get(114).getX(), ALLPoints.get(114).getY());
        canvas.drawPath(path07, Pnt);

        Log.d("face_is", "finish");

    }


}

