package au.edu.jcu.cp5030.ARCore;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.SharedCamera;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.util.ArrayList;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private Session session;
    private SharedCamera sharedCamera;
    private String cameraId;
    private ArFragment arFragment;
    private ModelRenderable modelRenderable;
    private ArrayList<Anchor> placedAnchors = new ArrayList<>();
    private ArrayList<AnchorNode> placedAnchorNodes = new ArrayList<>();

    private boolean installRequested = true;

    public MainActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);

        Button clearAllButton = findViewById(R.id.clearButton);
        clearAllButton.setOnClickListener(view -> clearAllAnchors());

        arFragment.setOnTapArPlaneListener((hitResult, plane, motionEvent) -> {
            clearAllAnchors();
            setupPlane(hitResult);
        });

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                // Create session.
                session = new Session(/* context= */ this);

                sharedCamera = session.getSharedCamera();
                cameraId = session.getCameraConfig().getCameraId();
                Config config = session.getConfig();
                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    config.setDepthMode(Config.DepthMode.AUTOMATIC);
                } else {
                    config.setDepthMode(Config.DepthMode.DISABLED);
                }
                session.configure(config);
            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }

            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                Log.e("Session creation error: ","Exception creating session", exception);
                return;
            }
        }

        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            Toast.makeText(this, "Camera not available. " +
                    "Try restarting the app.", Toast.LENGTH_LONG).show();
            session = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results){
        if(!CameraPermissionHelper.hasCameraPermission(this)){
            Toast.makeText(this, "Camera permission is needed to run this application",
                    Toast.LENGTH_LONG).show();
            if(!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)){
                //Permission denied with checking "Do not ask again"
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    private void setupPlane(HitResult hitResult){
        Anchor anchor = hitResult.createAnchor();
        placedAnchors.add(anchor);
        // Attach AnchorNode to an Anchor from the hit test
        AnchorNode anchorNode = new AnchorNode(anchor);
        placedAnchorNodes.add(anchorNode);
        anchorNode.setParent(arFragment.getArSceneView().getScene());
        createRenderable(anchorNode);
    }

    private void createRenderable(AnchorNode anchorNode){
        // Create 3D sphere using MaterialFactory and ModelRenderable to use as anchor markers
        MaterialFactory
                .makeOpaqueWithColor(this, new Color(android.graphics.Color.RED))
                .thenAccept(material -> {
                            ModelRenderable redSphereRenderable =
                                    ShapeFactory.makeSphere(0.07f, new Vector3(0.0f, 0.15f, 0.0f), material);

                            createModel(anchorNode, redSphereRenderable);
                });
    }

    private void createModel(AnchorNode anchorNode, ModelRenderable modelRenderable){
        TransformableNode node = new TransformableNode(arFragment.getTransformationSystem());
        node.setParent(anchorNode);
        node.setRenderable(modelRenderable);
        node.select();
    }

    private void clearAllAnchors(){
        for(AnchorNode anchorNode : placedAnchorNodes){
            arFragment.getArSceneView().getScene().removeChild(anchorNode);
            Objects.requireNonNull(anchorNode.getAnchor()).detach();
            anchorNode.setParent(null);
            anchorNode.removeChild(anchorNode);
            anchorNode.setRenderable(null);
        }
        placedAnchors.clear();
        placedAnchorNodes.clear();
    }

    private void clearSession(){
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        this.startActivity(i);
    }

    private void measureDistance(){
        if(placedAnchorNodes.size() == 0){

        }
    }
}