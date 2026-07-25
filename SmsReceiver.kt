package com.example.autopaynode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class SmsReceiver : BroadcastReceiver() {
    private val client = OkHttpClient()
    private val serverUrl = "https://yourdomain.com/api_verify.php" // رابط موقعك

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras
            if (bundle != null) {
                val pdus = bundle.get("pdus") as Array<*>
                for (pdu in pdus) {
                    val sms = SmsMessage.createFromPdu(pdu as ByteArray)
                    val sender = sms.displayOriginatingAddress
                    val messageBody = sms.messageBody

                    // مثال لفلترة رسائل فودافون كاش
                    if (sender.equals("VodafoneCash", ignoreCase = true) || sender.equals("InstaPay", ignoreCase = true)) {
                        parseAndSend(messageBody, sender)
                    }
                }
            }
        }
    }

    private fun parseAndSend(message: String, senderId: String) {
        try {
            // Regex لاستخراج المبلغ (مثال: تم تحويل مبلغ 500 ج.م)
            val amountRegex = Regex("مبلغ (\\d+) ج\\.م")
            // Regex لاستخراج الرقم (مثال: من الرقم 01012345678)
            val phoneRegex = Regex("01[0125][0-9]{8}")

            val amountMatch = amountRegex.find(message)
            val phoneMatch = phoneRegex.find(message)

            if (amountMatch != null && phoneMatch != null) {
                val amount = amountMatch.groupValues[1]
                val senderNumber = phoneMatch.value
                val walletType = if (senderId.contains("Vodafone")) "Vodafone" else "InstaPay"

                sendToServer(amount, senderNumber, walletType)
            }
        } catch (e: Exception) {
            Log.e("AutoPay", "Error parsing SMS", e)
        }
    }

    private fun sendToServer(amount: String, senderNumber: String, walletType: String) {
        val jsonObject = JSONObject().apply {
            put("secret_key", "AutoPay_Node_Secret_2026")
            put("amount", amount)
            put("sender_number", senderNumber)
            put("wallet_type", walletType)
        }

        val requestBody = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(serverUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("AutoPay", "Failed to send to server", e)
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("AutoPay", "Server Response: ${response.body?.string()}")
            }
        })
    }
}
