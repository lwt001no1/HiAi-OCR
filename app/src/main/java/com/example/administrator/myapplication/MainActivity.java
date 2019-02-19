package com.example.administrator.myapplication;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.huawei.hiai.vision.common.ConnectionCallback; //加载连接服务的回调函数
import com.huawei.hiai.vision.common.VisionBase; //加载连接服务的静态类
import com.huawei.hiai.vision.visionkit.common.BoundingBox;//加载矩形框类
import com.huawei.hiai.vision.visionkit.common.Frame; //加载Frame类
import com.huawei.hiai.vision.visionkit.text.TextConfiguration; //加载文本配置类
import com.huawei.hiai.vision.visionkit.text.Text; //加载文本结果类
import com.huawei.hiai.vision.visionkit.text.TextBlock; //加载文本Block表示类
import com.huawei.hiai.vision.visionkit.text.TextElement; //加载文本element表示类
import com.huawei.hiai.vision.visionkit.text.TextLine; //加载文本line表示类
import com.huawei.hiai.vision.visionkit.text.TextDetectType;//加载可支持的识别类型
import com.huawei.hiai.vision.text.TextDetector; //加载文本检测类

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnClickListener {

    private static final String TAG = "ocrActivity";

    private static final int REQUEST_CHOOSE_PHOTO_CODE = 2;
    private static final int REQUEST_TAKE_PHOTO_CODE = 1;

    private static final int TYPE_CHOOSE_PHOTO = 1;
    private static final int TYPE_TAKE_PHOTO = 3;
    private static final int TYPE_SHOW_RESULE = 2;

    private String path = Environment.getExternalStorageDirectory().getAbsolutePath()
            + "/HiAi-OCR/" + System.currentTimeMillis() + ".jpg";
    private Object mWaitResult = new Object();

    private Button mBtnTakePhoto;
    private Button mBtnChosePicture;
    private ImageView mImageView;
    private TextView mTxtView;
    private TextView mTxtViewOfSite;
    private TextView mTxtViewOfResult;

    private TextDetector mTextDetector;
    private Bitmap mBitmap;
    private String jsonObjectOfDetector;
    private int mEngineType;
    private Uri photoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImageView = (ImageView) findViewById(R.id.imageView);
        mBtnTakePhoto = (Button) findViewById(R.id.takePhoto);//拍照
        mBtnChosePicture = (Button) findViewById(R.id.chosePicture);//选择照片
        mTxtView = (TextView) findViewById(R.id.textView);
        mTxtViewOfSite = (TextView) findViewById(R.id.textView2);
        mTxtViewOfResult = (TextView) findViewById(R.id.textView3);
//        mTxtView.setMovementMethod(ScrollingMovementMetnhod.getInstance());
        mBtnTakePhoto.setOnClickListener(this);
        mBtnChosePicture.setOnClickListener(this);
        this.requestPermission();
        /* To connect vision service */
        VisionBase.init(getApplicationContext(), new ConnectionCallback() {
            @Override
            public void onServiceConnect() {
                Log.i(TAG, "onServiceConnect.");
            }

            @Override
            public void onServiceDisconnect() {
                Log.i(TAG, "onServiceDisconnect.");
            }
        });

        mThread.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        /* release ocr instance and free the npu resources*/
        if (mTextDetector != null) {
            mTextDetector.release();
        }
    }

    private void requestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }
    }

    @Override
    public void onClick(View v) {
        Log.d(TAG, "onClick !!! ");
        switch (v.getId()) {
            case R.id.takePhoto: {
                File file = new File(path);
                if (!file.getParentFile().exists())
                    file.getParentFile().mkdirs();
                Uri uri = FileProvider.getUriForFile(this, "com.android.auth.wtli", file);
                photoUri = uri;
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                startActivityForResult(intent, REQUEST_TAKE_PHOTO_CODE);
                break;
            }
            case R.id.chosePicture: {
                Log.d(TAG, "Select an image");
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_CHOOSE_PHOTO_CODE);
                break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_TAKE_PHOTO_CODE) {
            getBitmap(null, true);
        }
        if (requestCode == REQUEST_CHOOSE_PHOTO_CODE && resultCode == Activity.RESULT_OK) {
            if (data == null) {
                return;
            }
            Uri selectedImage = data.getData();
            getBitmap(selectedImage, false);
        }
    }

    private void getBitmap(Uri imageUri, boolean ifTakePhoto) {
        if (ifTakePhoto == false) {
            String[] pathColumn = {MediaStore.Images.Media.DATA};
            Cursor cursor = getContentResolver().query(imageUri, pathColumn, null, null, null);
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(pathColumn[0]);
        /* get image path */
            String picturePath;
            picturePath = cursor.getString(columnIndex);
            cursor.close();
            mBitmap = BitmapFactory.decodeFile(picturePath);
            Log.d(TAG, "url : " + imageUri.toString());
            mHander.sendEmptyMessage(TYPE_CHOOSE_PHOTO);
        } else if (ifTakePhoto == true) {
            Log.e("takePhotoPath: ", path);
            mBitmap = BitmapFactory.decodeFile(path);
            mHander.sendEmptyMessage(TYPE_TAKE_PHOTO);
        }
    }

    private Handler mHander = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int status = msg.what;
            Log.d(TAG, "handleMessage status = " + status);
            switch (status) {
                case TYPE_CHOOSE_PHOTO: {
                    if (mBitmap == null) {
                        Log.e(TAG, "bitmap is null !!!! ");
                        return;
                    }
//                    mImageView.setImageBitmap(mBitmap);
                    startDetect();
                    break;
                }
                case TYPE_TAKE_PHOTO: {
                    if (mBitmap == null) {
                        Log.e(TAG, "bitmap is null !!!! ");
                        return;
                    }
                    mImageView.setImageURI(photoUri);
                    startDetect();
                    break;
                }
                case TYPE_SHOW_RESULE: {
                    Text result = (Text) msg.obj;

                    if (result == null) {
                        mTxtView.setText("Failed to detect text lines, result is null.");
                        break;
                    }

                    String textValue = result.getValue();
                    //解析最大的区域
                    Point[] cPoints = result.getCornerPoints();
                    String points = "文字总区域" + "\n";
                    for (int i = 0; i < cPoints.length; i++) {
                        points += cPoints[i].toString();
                        points += "\n";
                    }

                    //解析文字小区域
                    List<TextBlock> blocks = result.getBlocks();
                    TextBlock tBlock = blocks.get(0);
                    List<TextLine> lines = tBlock.getTextLines();
                    String smallPoints = "文字分区域" + "\n";
                    for (int j = 0; j < lines.size(); j++) {
                        TextLine tLines = lines.get(j);
                        Point[] sPoints = tLines.getCornerPoints();
                        smallPoints += j + 1 + ": " + "\n";
                        for (int n = 0; n < sPoints.length; n++) {
                            smallPoints += sPoints[n].toString();
                            smallPoints += "\n";
                        }
                        smallPoints += "Value: " + tLines.getValue() + "\n";
                    }
                    drawRectangles(cPoints, lines);
                    Log.d(TAG, "OCR Detection succeeded.");
                    mTxtView.setText("text in image: " + textValue);
                    mTxtViewOfSite.setText(smallPoints);
                    mTxtViewOfResult.setText(points);
                    break;
                }
                default:
                    break;
            }
        }
    };

    private void startDetect() {
        mTxtView.setText("ocr result");
        synchronized (mWaitResult) {
            mWaitResult.notifyAll();
        }
    }

    private Thread mThread = new Thread(new Runnable() {
        @Override
        public void run() {
            mTextDetector = new TextDetector(getApplicationContext());
            TextDetector textDetector = mTextDetector;
            while (true) {
                try {
                    synchronized (mWaitResult) {
                        mWaitResult.wait();
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, e.getMessage());
                }

                Log.d(TAG, "start to detect ocr.");
                /* create frame and set images*/
                Frame frame = new Frame();
                frame.setBitmap(mBitmap);

                /* create a TextDetector instance firstly */
//                TextDetector textDetector = new TextDetector(mContext);

		        /* create a TextConfiguration instance here, */
                TextConfiguration config = new TextConfiguration();
                /* and set the EngineType as focus shoot ocr */
                config.setEngineType(TextDetectType.TYPE_TEXT_DETECT_FOCUS_SHOOT);
                textDetector.setTextConfiguration(config);

                /* start to detect and get the json object, which can be analyzed as Text */
                JSONObject jsonObject = textDetector.detect(frame, null);
                Log.d(TAG, "end to detect ocr. json: " + jsonObject.toString());  /*jsonObject never be null*/
                jsonObjectOfDetector = jsonObject.toString();
                resolveJsonObject(jsonObject);
                /* analyze the result */
                Text result = textDetector.convertResult(jsonObject);
                /* do something follow your heart*/
                Message msg = new Message();
                msg.what = TYPE_SHOW_RESULE;
                msg.obj = result;

                mHander.sendMessage(msg);

                textDetector.release();
            }
        }
    });

    private String getPhotoFileName() {
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        return "IMG_" + dateFormat.format(date);
    }

    private Bitmap getBitmapFromUri(Context context, Uri uri) {
        ParcelFileDescriptor parcelFileDescriptor;
        Bitmap mBitmap = null;
        try {
            parcelFileDescriptor = context.getContentResolver()
                    .openFileDescriptor(uri, "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor
                    .getFileDescriptor();
            mBitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor);
            parcelFileDescriptor.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mBitmap;
    }

    private void resolveJsonObject(JSONObject jsonObject) {
        ArrayList<String> jsonStringArr = new ArrayList();
        try {
            String jsonString = jsonObject.getString("common_text");
            Log.e("jsonString", jsonString);
        } catch (JSONException e) {
            Log.e("resolveJsonError", e.getLocalizedMessage());
        }
    }

    private void drawRectangles(Point[] cornerPoints, List<TextLine> lines) {
        int left, top, right, bottom;
        Bitmap mutableBitmap = mBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);
        //**********bigRectangle
        Paint paint = new Paint();
        left = cornerPoints[0].x;
        top = cornerPoints[0].y;
        right = cornerPoints[2].x;
        bottom = cornerPoints[2].y;
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);//不填充
        paint.setStrokeWidth(10);  //线的宽度
        canvas.drawRect(left, top, right, bottom, paint);
        //************smallRectangle
        for (int i = 0; i < lines.size(); i++) {
            TextLine tLines = lines.get(i);
            Point[] sPoints = tLines.getCornerPoints();
            for (int j = 0; j < sPoints.length; j++) {
                left = sPoints[0].x;
                top = sPoints[0].y;
                right = sPoints[2].x;
                bottom = sPoints[2].y;
                paint.setColor(Color.GREEN);
                paint.setStyle(Paint.Style.STROKE);//不填充
                paint.setStrokeWidth(10);  //线的宽度
                canvas.drawRect(left, top, right, bottom, paint);
            }
        }
        mImageView.setImageBitmap(mutableBitmap);//img: 定义在xml布局中的ImagView控件
    }
}
