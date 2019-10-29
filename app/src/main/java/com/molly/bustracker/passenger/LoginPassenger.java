package com.molly.bustracker.passenger;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.molly.bustracker.R;
import com.molly.bustracker.ServiceOption;
import com.molly.bustracker.UserClient;
import com.molly.bustracker.model.User;

import static android.text.TextUtils.isEmpty;

public class LoginPassenger extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    //Firebase


    // widgets
    private EditText mEmail, mPassword;
    private ProgressBar mProgressBar;

    private Button signin_btn;
    private Button signup_btn;

    private FirebaseFirestore db;

    //private User currUser;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_passenger);

        signin_btn = findViewById(R.id.signinbtn);
        signup_btn = findViewById(R.id.signupbtn);

        mEmail = findViewById(R.id.emailtxt);
        mPassword = findViewById(R.id.pwtxt);
        mProgressBar = findViewById(R.id.progressBarLgn);

        mAuth = FirebaseAuth.getInstance();

        db = FirebaseFirestore.getInstance();

        hideSoftKeyboard();


        signin_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                signIn();
            }
        });

        signup_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent toSignUp = new Intent(LoginPassenger.this, SignUpPassenger.class);
                startActivity(toSignUp);
            }
        });

    }


    private void showDialog() {
        mProgressBar.setVisibility(View.VISIBLE);
        signin_btn.setVisibility(View.GONE);

    }

    private void hideDialog() {
        if (mProgressBar.getVisibility() == View.VISIBLE) {
            mProgressBar.setVisibility(View.GONE);
            signin_btn.setVisibility(View.VISIBLE);
        }
    }

    private void hideSoftKeyboard() {
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }


    private void signIn() {
        //check if the fields are filled out
        if (!isEmpty(mEmail.getText().toString())
                && !isEmpty(mPassword.getText().toString())) {
            Log.d(TAG, "onClick: attempting to authenticate.");

            showDialog();

            mAuth.signInWithEmailAndPassword(mEmail.getText().toString(),
                    mPassword.getText().toString()).addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    if (task.isSuccessful()) {
                        hideDialog();
                        FirebaseUser user = mAuth.getCurrentUser();
                        DocumentReference userRef = db.collection(getString(R.string.collection_passengers))
                                .document(user.getUid());



                        userRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                if (task.isSuccessful()) {
                                    if (task.getResult().toObject(User.class) != null) {
                                        Log.d(TAG, "onComplete: successfully set the user client.");
                                        User user = task.getResult().toObject(User.class);
                                        user = user;
                                        ((UserClient) (getApplicationContext())).setUser(user);
                                    }
                                }
                            }
                        });

                        Toast.makeText(LoginPassenger.this, "Authentication Success", Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(LoginPassenger.this, ServiceOption.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(LoginPassenger.this, "Authentication Failed", Toast.LENGTH_SHORT).show();
                        hideDialog();
                    }
                }
            });
        }
    }
}
