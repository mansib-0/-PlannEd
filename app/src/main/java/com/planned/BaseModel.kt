package com.planned

object BaseModel {
    private const val X_MEAN = 83.828003f
    private const val X_STD = 64.579079f
    
    private val W1 = floatArrayOf(0.007367f, -0.818943f, -0.231115f, -3.046499f, -0.156305f, -0.011723f, -0.003206f, -0.207583f)
    private val b1 = floatArrayOf(-0.024611f, 1.565254f, 0.441733f, 5.822804f, 0.298747f, -0.021768f, -0.091328f, 0.396755f)
    private val W2 = floatArrayOf(-0.006618f, -1.332371f, -0.376011f, -4.956472f, -0.254299f, -0.189897f, -0.057664f, -0.337725f)
    private val b2 = 60.497413f
    
    fun predictPadding(predictedDuration: Int): Int {
        val x = (predictedDuration.toFloat() - X_MEAN) / X_STD
        
        // Forward pass: Hidden layer (ReLU)
        val a1 = FloatArray(8)
        for (i in 0 until 8) {
            val z1 = x * W1[i] + b1[i]
            a1[i] = if (z1 > 0f) z1 else 0f
        }
        
        // Output layer
        var pred = b2
        for (i in 0 until 8) {
            pred += a1[i] * W2[i]
        }
        
        return roundUpToNearest5(pred).coerceAtMost(60).coerceAtLeast(0)
    }
}
