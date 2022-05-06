package com.matrixview

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.show_matrix_view).setOnClickListener {
            startActivity(Intent(this, MatrixActivity::class.java))
        }

        findViewById<View>(R.id.show_rotated_circles_view).setOnClickListener {
            startActivity(Intent(this, RotatingCirclesActivity::class.java))
        }
    }
}