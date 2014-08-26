package com.example.opencv_cam;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;
import android.annotation.SuppressLint;
import android.app.Activity;


public class MainActivity extends Activity implements CvCameraViewListener2,
				OnTouchListener {
	
	public enum ViewType {RGB, Gray, Canny, Bokeh};
	public enum CaptureType {Filtered, Original, Bokeh, Mask, WaterShed};
	
	private static final String TAG = "OpenCV::OutOfFocus";
	
	private JavaCameraView			mOpenCvCameraView;
	Button captureButton;	
	
	// status variables
	private int						nViewWidth;
	private int						nViewHeight;
	private boolean					mIsEditMode = false;
	
	private ViewType				nViewMode;
	private CaptureType				nCaptureMode;
	
	// menu variables	
	private MenuItem				mItemEnableAF = null;
	private boolean					mIsEnableAF = true;
	private MenuItem				mItemEnableFL = null;
	private boolean					mIsEnableFL = false;
	private MenuItem[]				mViewOption;
	private SubMenu					mViewOptionSub;
	
	private MenuItem				mItemEnableBokeh = null;
	private boolean					mIsBokeh = false;
	private MenuItem[]				mBokehSize;
	private SubMenu					mBokehMenu;
	private MenuItem				mItemShowMask;
	private boolean					mShowMask;
	private MenuItem				mItemResetMask;
	
	private boolean					bChangeView = false;
	private boolean					bChangeMask = false;
	
	private MenuItem				mItemSave;
	
	// filter variables
	private int						nDivideSize;
	private	int						nBokehSize;
	private Mat						mKernel;
	private boolean					isChangeFilter;
	
	private int						nTouchCnt;
	
	// adjusting bokeh filter variables
	private boolean					isChangeDivide;
	
	// mask variables
	private Point					pStart;
	private Point					pMiddle;
	private Point					pEnd;
		
	// image matrix which have data
	private Mat						mRgba;
	private Mat						mGray;	
	private Mat						mPrevRgb;
	private Mat						mPrevGray;
	private Mat						mCanny;
	
	private Mat						mMask;
	private Mat						mDraw;
	private Mat						mContour;
	
	private Mat						mSmallFrame;
	private Mat						mFilteredFrame;
	private Mat						mMaskedFrame;
	
	private Mat						mWaterShed;
	private Mat						mMarkers;
	private Mat						mThreshold;
	
	// image matrix for select and view
	private Mat						mSelectView;
	private Mat						mViewFrame;
	
	/*
	static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        } else {
            System.loadLibrary("my_jni_lib1");
            System.loadLibrary("my_jni_lib2");
        }
    }
	*/
	
	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
		@SuppressLint("ClickableViewAccessibility")
		@Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");                                      
                    
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(MainActivity.this);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    private void createKernel (int diameter)
    {
    	Log.i(TAG, "Creating Kernel...");
    	
    	int rad, radsq;
    	float filterval, filter_total = 0;
    	double[] buff;
    	
    	if (diameter < 2)
    	{
    		diameter = 2;	
    	}
    	
    	rad = (diameter+1) / 2;
    	radsq = rad * rad;
    	buff = new double[diameter * diameter];
    	
    	Log.i(TAG, "Initializing kernel...");
    	mKernel.release();
    	mKernel.create(diameter, diameter, CvType.CV_64F);    
    	mKernel.get(0, 0, buff);
    	Log.i(TAG, "Initializing kernel success");
    	
    	for (int k=0; k < diameter; k++)
    	{
    		int xdiff = (k-rad) * (k-rad);
    		int col = k * diameter;
    		
    		for (int j=0; j < diameter; j++)
    		{
    			float diff = (float)xdiff + (float)((j-rad) * (j-rad));
    			
    			if (diff > radsq)
    			{
    				filterval = 0;
    			}
    			else
    			{
    				//filterval = (float) Math.cos((diff / (float)radsq) * (Math.PI / 4));
    				filterval = 1;
    				filter_total += filterval;
    			}
    			  
    			buff[col+j] = filterval;
    			//mKernel.put(k, j, filterval);
    		}
    	}
    	Log.i(TAG, "Put value in Kernel Success");
    	    	
    	for (int k=0; k < diameter; k++)
    	{
    		int col = k * diameter;
    		for (int i=0; i < diameter; i++)
    		{
    			buff[col + i] = buff[col + i] / filter_total;
    		}
    	}
    	Log.i(TAG, "Scale Value in Kernel Success");
    	
    	mKernel.put(0, 0, buff);
    	
    	isChangeFilter = true;
    }
    
    public MainActivity()
    {
    	Log.i(TAG, "Installed new " + this.getClass());
    }

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "called onCreate");
		
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		setContentView(R.layout.activity_main);
		
		// bokeh filter initialize
		Log.i(TAG, "Initializing  Filter...");
		nViewMode = ViewType.RGB;
		nDivideSize = 4;
		nBokehSize = 5;
		//mKernel = new Mat();
		//this.createKernel(nBokehSize);
		isChangeFilter = false;
		isChangeDivide = true;
		Log.i(TAG, "Complete Initialize Filter");
		
		// camera initialize
		mOpenCvCameraView = (JavaCameraView) findViewById (R.id.java_surface_view);
		
		mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
		mOpenCvCameraView.setCvCameraViewListener(this);		
		
		// interface initialize
		captureButton = (Button) findViewById(R.id.button_capture);
		captureButton.setOnClickListener(
				new View.OnClickListener() {
					
					@Override
					public void onClick(View v) {
						// TODO Auto-generated method stub	
						if (mIsEditMode == false)
						{
							InitMask();
						}
					}
				}				
	    );
		
		Log.i(TAG, "onCreate Complete");		
	}

	@Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }
	
	@Override
	public void onResume()
	{
		super.onResume();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.enableView();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, this, mLoaderCallback);
	}
	
	public void onDestroy()
	{
		super.onDestroy();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
	}	
	
	
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		//getMenuInflater().inflate(R.menu.main, menu);
		Log.i(TAG, "called onCreateOptionsMenu");
		
		return true;
	}

	@SuppressLint("SimpleDateFormat")
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		/*
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		
		return super.onOptionsItemSelected(item);
		*/
		String toastMessage = new String();
		Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
		
		if (mIsEditMode)
		{
			if (item.getGroupId() == 1)
			{
				int id = item.getItemId();
				
				//isChangeDivide = true;
				nDivideSize = id+1;
				toastMessage = "Change Bokeh Size to " + (nDivideSize * nBokehSize);
				
				CreateBokehImage();
			}
			else if (item == mItemSave)
			{
				SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-d_HH-mm-ss");
				
				String currentDate = date.format(new Date());
				String filename = Environment.getExternalStorageDirectory().getPath() +
						"/bokeh_effect" + currentDate + ".jpg";
				
				Mat mBgrImg = new Mat();
				Imgproc.cvtColor(mViewFrame, mBgrImg, Imgproc.COLOR_RGBA2BGRA);
				Highgui.imwrite(filename, mBgrImg);
				
				mIsEditMode = false;
				nViewMode = ViewType.RGB;
			}
			else if (item.getGroupId() == 2)
			{
				int id = item.getItemId();
				
				//mSelectView.release();
				switch (id)
				{
				case 0:
					nCaptureMode = CaptureType.Filtered;
					//mMaskedFrame.copyTo(mSelectView);
					break;
					
				case 1:
					nCaptureMode = CaptureType.Original;
					//mRgba.copyTo(mSelectView);					
					break;
					
				case 2:
					nCaptureMode = CaptureType.Bokeh;
					//mFilteredFrame.copyTo (mSelectView);					
					break;

				case 3:
					nCaptureMode = CaptureType.Mask;
					//mMask.copyTo(mSelectView);					
					break;
				}			
				
				EditModeViewSelect();
			}
			else if (item == mItemResetMask)
			{
				InitMask();
			}
			
			if (item != mItemSave)
			{
				bChangeView = true;
			}
		}
		else
		{
			if (item == mItemEnableAF)
			{
				if (mIsEnableAF)
				{
					toastMessage = "Toggle AF Off";
					mOpenCvCameraView.clearFocus();
				}
				else
				{
					toastMessage = "Toggle AF On";
					mOpenCvCameraView.findFocus();
				}
				mIsEnableAF = !mIsEnableAF;
			}
			else if (item == mItemEnableFL)
			{
				toastMessage = "Toggle Flash";
			}
			else if (item.getGroupId() == 1)
			{
				int id = item.getItemId();
				
				isChangeDivide = true;
				nDivideSize = id+1;
				toastMessage = "Change Bokeh Size to " + (nDivideSize * nBokehSize);
			}
			else if (item.getGroupId() == 2)
			{
				int id = item.getItemId();
				
				switch (id)
				{
				case 0:
					nViewMode = ViewType.RGB;					
					break;
					
				case 1:
					nViewMode = ViewType.Gray;
					break;
				
				case 2:
					nViewMode = ViewType.Canny;
					break;
					
				case 3:
					nViewMode = ViewType.Bokeh;
					break;
				}
			}
		}
		
		if (toastMessage.length() != 0)
		{
			Toast toast = Toast.makeText(this, toastMessage, Toast.LENGTH_LONG);
			toast.show();
		}
			
		return true;
	}
	
	// camera initialize
	public void onCameraViewStarted (int width, int height)
	{
		Log.i(TAG, "OnCameraViewStarted Start");
				
		// initializing mat
		mViewFrame = new Mat();
        mKernel = new Mat();
        mSmallFrame = new Mat();
        mFilteredFrame = new Mat();        
        mMaskedFrame = new Mat();
        
        mPrevGray = new Mat();
        mPrevRgb = new Mat();
        mCanny = new Mat();
        
		createKernel(nBokehSize);
		
		//mMask = new Mat();
        //mDraw = new Mat();
        //mContour = new Mat();
        
        //mWaterShed = new Mat();
        //mMarkers = new Mat();
		
		// variable initialize
		//pStart = new Point();
		//pMiddle = new Point();
		//pEnd = new Point();
		
		Log.i(TAG, "OnCameraViewStarted End");
	}
	
	// 
	public void onCameraViewStopped()
	{
	}

	@Override
	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		// TODO Auto-generated method stub		
		if (mIsEditMode == false)
		{
			mRgba = inputFrame.rgba();
			mGray = inputFrame.gray();			
					
			switch (nViewMode)
			{
			case RGB:
				mSelectView = mRgba;
				break;
				
			case Gray:
				mSelectView = mGray;
				break;
				
			case Canny:
				//Imgproc.erode(mGray, mGray, new Mat());
				//Imgproc.erode(mGray, mGray, new Mat());
				Imgproc.Canny(mGray, mCanny, 80, 100, 3, true);
				mSelectView = mCanny;
				break;
				
			case Bokeh:				
				if (isChangeDivide)
				{
					if (nDivideSize == 0)
					{
						nDivideSize = 1;
					}
					mSmallFrame.create ((int)(mRgba.size().height / nDivideSize),
						(int)(mRgba.size().width / nDivideSize), mRgba.type());
		
					isChangeDivide = false;
				}
		
				Imgproc.resize(mRgba, mSmallFrame, mSmallFrame.size());
				Imgproc.filter2D(mSmallFrame, mSmallFrame, -1, mKernel);
				Imgproc.resize(mSmallFrame, mFilteredFrame, mRgba.size());
				
				mSelectView = mFilteredFrame;
				break;
			}
			
			mSelectView.copyTo (mViewFrame);
			
			mRgba.copyTo(mPrevRgb);
			mGray.copyTo(mPrevGray);
		}
		else
		{		
			/*
			switch (nCaptureMode)
			{
			case Filtered:
				mSelectView = mMaskedFrame;
				break;
				
			case Original:
				mSelectView = mPrevRgb;
				break;
				
			case Bokeh:
				mSelectView = mFilteredFrame;
				break;
				
			case Mask:
				mSelectView = mMask;
				break;
				
			case WaterShed:
				mSelectView = mWaterShed;
				break;
			}
			*/
			
			/*
			if (isChangeDivide)
			{
				mSmallFrame.create ((int)(mRgba.size().height / nDivideSize),
						(int)(mRgba.size().width / nDivideSize), mRgba.type());
					
				Imgproc.resize(mRgba, mSmallFrame, mSmallFrame.size());
				Imgproc.filter2D(mSmallFrame, mSmallFrame, -1, mKernel);
				Imgproc.resize(mSmallFrame, mFilteredFrame, mRgba.size());
					
				//mViewFrame = mFilteredFrame;				
				mFilteredFrame.copyTo(mMaskedFrame);
				mRgba.copyTo(mMaskedFrame, mWaterShed);
				
				isChangeDivide = false;
			}			
			*/
				
			/*
			if (bChangeMask)
			{				
				mRgba.copyTo(mMaskedFrame, mWaterShed);				
				
				bChangeMask = false;
			}
			*/
			
			mSelectView.copyTo(mViewFrame);
			//mViewFrame = mSelectView;
		}
						
		return mViewFrame;
		//return inputFrame.rgba();
	}
	
	@Override
	@SuppressLint({ "SimleDateFormat", "ClickableViewAccessibility", "SimpleDateFormat" })
	public boolean onTouchEvent (MotionEvent event)
	{
		Log.i(TAG,"onTouch event");
		
		if (mIsEditMode == false)
		{
			//mIsEditMode = true;			
			//nCaptureMode = CaptureType.Filtered;
			
			//Log.i(TAG, "Invoke AsyncTask...");
			
			//InitMask();
		}
		else
		{
			int xPos, yPos;
			int xPosOnFrame, yPosOnFrame;
			int nTouchAction;
			
			nTouchAction = event.getActionMasked();
			Log.i(TAG,"Action" + nTouchAction);
			
			xPos = (int)event.getX();
			yPos = (int)event.getY();
			
			Log.i(TAG,"touch events on " + xPos + " " + yPos);
			
			xPosOnFrame = xPos * mRgba.width() / mOpenCvCameraView.getWidth();
			yPosOnFrame = yPos * mRgba.width() / mOpenCvCameraView.getWidth();
			
			Log.i(TAG,"touch events Frame " + xPosOnFrame + " " + yPosOnFrame);
			
			//Point p = new Point(xPosOnFrame, yPosOnFrame);
			
			switch (nTouchAction)
			{
			case MotionEvent.ACTION_DOWN:
			//	Log.i(TAG,"ACTION_DOWN");
				pStart.x = xPosOnFrame;
				pStart.y = yPosOnFrame;
				
				pMiddle.x = xPosOnFrame;
				pMiddle.y = yPosOnFrame;
				
				break;
				
			case MotionEvent.ACTION_MOVE:
				//Log.i(TAG,"ACTION_MOVE");
										
				pEnd.x = xPosOnFrame;
				pEnd.y = yPosOnFrame;
				
				Core.line(mDraw, pMiddle, pEnd, new Scalar(255 - nTouchCnt), 2);
				Core.line(mSelectView, pMiddle, pEnd, new Scalar(0, 255, 0, 255), 2);
				
				pMiddle.x = xPosOnFrame;
				pMiddle.y = yPosOnFrame;
				
				/*
				xPos = (int)event.getX();
				yPos = (int)event.getY();
				
				Log.i(TAG,"touch events on " + xPos + " " + yPos);
				
				xPosOnFrame = xPos * mRgba.width() / mOpenCvCameraView.getWidth();
				yPosOnFrame = yPos * mRgba.width() / mOpenCvCameraView.getWidth();
				
				Log.i(TAG,"touch events Frame " + xPosOnFrame + " " + yPosOnFrame);
				
				Point p = new Point(xPosOnFrame, yPosOnFrame);
				Core.circle (mMask, p, 4, new Scalar(255 - nTouchCnt), -1);
				//Core.circle (mMask, p, 2, new Scalar(255, 255, 255, 255), -1);				
				*/
				
				break;
			
			case MotionEvent.ACTION_UP:
				//cv::circle(markers, cv::Point(5,5), 3, CV_RGB(255,255,255), -1);
				//Core.circle(mMask, new Point(5,5), 4, new Scalar(1), -1);
								
				//Core.circle (mMask, p, 4, new Scalar(255 - nTouchCnt), -1);
					
				pEnd.x = xPosOnFrame;
				pEnd.y = yPosOnFrame;
				
				Core.line (mDraw, pMiddle, pEnd, new Scalar(255 - nTouchCnt), 2);
				Core.line(mSelectView, pMiddle, pEnd, new Scalar(0, 255, 0, 255), 2);
				Core.line (mDraw, pEnd, pStart, new Scalar(255 - nTouchCnt), 2);
				Core.line(mSelectView, pEnd, pStart, new Scalar(0, 255, 0, 255), 2);
				
				List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
				
				Imgproc.findContours(mDraw, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);
				Imgproc.drawContours(mContour, contours, -1, new Scalar(255, 255, 255), -1);				
				
				//mContour.copyTo(mMask);
				Imgproc.blur(mContour, mMask, new Size(10, 10));
				
				//mMask.convertTo(mMarkers, CvType.CV_32S);
				//Imgproc.cvtColor(mRgba, mPrevRgb, Imgproc.COLOR_RGBA2RGB);				
				//Imgproc.watershed(mPrevRgb,  mMarkers);
								
			    //mMarkers.convertTo(mWaterShed, CvType.CV_8U);
				
				/*
				byte[] buf = new byte[1];
				double[] index;
			    for (int i = 0; i < mMarkers.rows(); i++)
			    {
			        for (int j = 0; j < mMarkers.cols(); j++)
			        {
			            //int index = mMarkers.at<int>(i,j);
			            index  = mMarkers.get(i, j);			        	
			            
			            if ((index[0] <= 255) && (index[0] >= (255 - nTouchCnt)))
			            {
			                //dst.at<cv::Vec3b>(i,j) = colors[index-1];
			            	buf[0] = 127;
			            	mWaterShed.put(i, j, buf);
			            }
			            else
			            {
			                //dst.at<cv::Vec3b>(i,j) = cv::Vec3b(0,0,0);
			            	buf[0] = 0;
			            	mWaterShed.put(i, j, buf);
			            }
			        }
			    }			    
			    */
			    
			    nTouchCnt++;
			    //bChangeMask = true;
			    
			    AdjustMasktoImage ();
			    EditModeViewSelect();
			    
			    break;
				
			default:
				break;
			}			
		}
		
		return false;
	}
	
	
	private void InitMask ()
	{		
		// initialize Matrixes for Mask
		//mMask.create (mRgba.size(), mRgba.type());
		mMask = new Mat(mRgba.size(), CvType.CV_8U);
		mMask = Mat.zeros(mMask.size(), mMask.type());
		
		mDraw = new Mat(mRgba.size(), CvType.CV_8U);
		mDraw = Mat.zeros (mDraw.size(), mDraw.type());
		
		mContour = new Mat (mRgba.size(), CvType.CV_8U);
		mContour = Mat.zeros(mContour.size(), mContour.type());
		
		mMaskedFrame = new Mat();
		//mMarkers.create (mRgba.size(), CvType.CV_32SC1);
		//mMarkers = Mat.zeros(mMarkers.size(), mMarkers.type());
		mWaterShed = new Mat(mRgba.size(), CvType.CV_8U);
		mWaterShed = Mat.zeros(mWaterShed.size(), mWaterShed.type());
		
		mMarkers = new Mat();
		
		// 
		isChangeDivide = false;
		bChangeMask = false;
		
		nTouchCnt = 0;
		
		//Imgproc.Canny(mGray, mCanny, 0, 100, 3, true);
		                
		// initialize kernel and filtered image
		createKernel(nBokehSize);
		CreateBokehImage ();
		AdjustMasktoImage ();
		
		// Touch variable initialize
		pStart = new Point();
		pMiddle = new Point();
		pEnd = new Point();
		
		// convert to edit mode
		mIsEditMode = true;			
		nCaptureMode = CaptureType.Filtered;
		
		mSelectView = new Mat();
		mMaskedFrame.copyTo(mSelectView);
	}
	
	public static class asyncTask extends AsyncTask 
	{
		protected void onPreExecute()
		{
			Log.i(TAG, "Start AsyncTask...");
		}
		
		protected Object doInBackground(Object... params) {
			// TODO Auto-generated method stub
			return null;
		}
		
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		// TODO Auto-generated method stub
		menu.clear();
		
		if (mIsEditMode)
		{			
			mBokehMenu = menu.addSubMenu("Bokeh Size");
			mBokehSize = new MenuItem[8];			
			for (int idx = 0; idx < 8; idx++)
			{
				mBokehSize[idx] = mBokehMenu.add(1, (idx), Menu.NONE, 
						Integer.valueOf((idx+1)*5).toString());
			}
			
			mViewOptionSub = menu.addSubMenu("View Option");
			
			mViewOption = null;
			mViewOption = new MenuItem[4];
			mViewOption[0] = mViewOptionSub.add(2, 0, Menu.NONE,
					String.valueOf("Filtered"));
			mViewOption[1] = mViewOptionSub.add(2, 1, Menu.NONE,
					String.valueOf("Original"));
			mViewOption[2] = mViewOptionSub.add(2, 2, Menu.NONE,
					String.valueOf("Bokeh"));
			mViewOption[3] = mViewOptionSub.add(2, 3, Menu.NONE,
					String.valueOf("Mask"));
						
			mItemResetMask = menu.add ("Reset Mask");
			
			mItemSave = menu.add("Save Pictures");
		}
		else
		{
			mItemEnableAF = menu.add("toggle AF On/Off");
			mItemEnableFL = menu.add("toggle Flash");
			
			mBokehMenu = menu.addSubMenu("Bokeh Size");
			mBokehSize = new MenuItem[8];
			for (int idx = 0; idx < 8; idx++)
			{
				mBokehSize[idx] = mBokehMenu.add(1, (idx), Menu.NONE, 
						Integer.valueOf((idx+1)*5).toString());
			}
			
			mViewOptionSub = menu.addSubMenu("View Option");
			mViewOption = new MenuItem[4];
			mViewOption[0] = mViewOptionSub.add(2, 0, Menu.NONE,
					String.valueOf("RGB"));
			mViewOption[1] = mViewOptionSub.add(2, 1, Menu.NONE,
					String.valueOf("Gray"));
			mViewOption[2] = mViewOptionSub.add(2, 2, Menu.NONE,
					String.valueOf("Canny"));
			mViewOption[3] = mViewOptionSub.add(2, 3, Menu.NONE,
					String.valueOf("Bokeh"));
		}
		
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		// TODO Auto-generated method stub
		return false;
	}
	
	public void CreateBokehImage ()
	{
		mSmallFrame.create ((int)(mRgba.size().height / nDivideSize),
				(int)(mRgba.size().width / nDivideSize), mRgba.type());
			
		Imgproc.resize(mRgba, mSmallFrame, mSmallFrame.size());
		Imgproc.filter2D(mSmallFrame, mSmallFrame, -1, mKernel);
		Imgproc.resize(mSmallFrame, mFilteredFrame, mRgba.size());
			
		//mViewFrame = mFilteredFrame;				
		//mFilteredFrame.copyTo(mMaskedFrame);
		//mRgba.copyTo(mMaskedFrame, mWaterShed);
		AdjustMasktoImage();
	}
				
	public void AdjustMasktoImage()
	{
		Log.i(TAG, "AdjustMasktoImage :: Start");
		
		//mRgba.copyTo(mMaskedFrame, mWaterShed);
		int rows = mMask.rows();
		int cols = mMask.cols();
		
		//float[] buf_org = new float[mMask.cols() * mMask.rows()];
		//float[] buf_Mask = new float[mMask.cols() * mMask.rows()];
		byte[] buf_org = new byte[mRgba.cols() * mRgba.rows() * 4];
		byte[] buf_bokeh = new byte[mRgba.cols() * mRgba.rows() * 4];
		byte[] buf_Mask = new byte[mRgba.cols() * mRgba.rows() * 4];
		byte[] index = new byte[mMask.cols() * mMask.rows()];
		
		/*
		Mat mTemp = new Mat();
		Mat mImgLab_org = new Mat();
		Imgproc.cvtColor(mRgba, mTemp, Imgproc.COLOR_RGBA2RGB);
		Imgproc.cvtColor(mTemp, mImgLab_org, Imgproc.COLOR_RGB2Lab);
		
		Mat mImgLab_Mask = new Mat();
		Imgproc.cvtColor(mFilteredFrame, mTemp, Imgproc.COLOR_RGBA2RGB);
		Imgproc.cvtColor(mTemp, mImgLab_Mask, Imgproc.COLOR_RGB2Lab);
		*/
		
		mMask.get(0, 0, index);
		//Log.i(TAG, "get data array from mMask");
		//mImgLab_org.get(0, 0, buf_org);
		mRgba.get(0, 0, buf_org);
		//Log.i(TAG, "get data array from Original Image");
		//mImgLab_Mask.get(0, 0, buf_Mask);
		mFilteredFrame.get (0, 0, buf_bokeh);
		//Log.i(TAG, "get data array from Masked Image");
		mMaskedFrame.get (0, 0, buf_Mask);
		//mWaterShed.get(0, 0, buf);
		
		int cur_pos = 0;
	    for (int i = 0; i < rows; i++)
	    {	    	
	        for (int j = 0; j < cols; j++)
	        {
	        	int mat_pos = cur_pos * 4;
	        	int maskval = index[cur_pos] & 0xff;
	        	
	        	
	        	buf_Mask[mat_pos] = (byte) ((buf_bokeh[mat_pos] * (255-maskval) / 255)
	        			+ (buf_org[mat_pos]  * maskval / 255));	        	
	        	mat_pos++;
	        	buf_Mask[mat_pos] = (byte) ((buf_bokeh[mat_pos]  * (255-maskval) / 255)
	        			+ (buf_org[mat_pos] * maskval / 255));
	        	mat_pos++;
	        	buf_Mask[mat_pos] = (byte) ((buf_bokeh[mat_pos] * (255-maskval) / 255)
	        			+ (buf_org[mat_pos] * maskval / 255));
	        	mat_pos++;
	        	
	        	buf_Mask[mat_pos] = -128;
	        		        	
	        	cur_pos++;
	        	/*
	            if (index[row_cur + j] > 0)
	            {
	                //dst.at<cv::Vec3b>(i,j) = colors[index-1];
	            	buf[row_cur + j] = -127;
	            }
	            else
	            {
	                //dst.at<cv::Vec3b>(i,j) = cv::Vec3b(0,0,0);
	            	buf[row_cur + j] = 0;
	            }
	            */
	        }
	    }
	    
	    mMaskedFrame.put(0, 0, buf_Mask);
	    //Imgproc.cvtColor(mImgLab_Mask, mMaskedFrame, Imgproc.COLOR_Lab2RGB);
	    
	    Log.i(TAG, "AdjustMasktoImage :: End");
	}	
	
	private void EditModeViewSelect ()
	{
		//mSelectView.release();
		//mSelectView.create(mRgba.size(), mRgba.type());
		
		
		switch (nCaptureMode)
		{
		case Filtered:
			mSelectView = mMaskedFrame.clone();
			break;
			
		case Original:
			mSelectView = mRgba.clone();
			break;
			
		case Bokeh:
			mSelectView = mFilteredFrame.clone();
			break;
			
		case Mask:
			mSelectView = mMask.clone();
			break;
		}
		
	}
}



