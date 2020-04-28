package net.bestia.loginserver.factorauth

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.awt.image.BufferedImage


@RestController
@RequestMapping("factor")
class AuthenticatorController {

  @GetMapping("totp", produces = [MediaType.IMAGE_PNG_VALUE])
  fun addAuthenticator(): BufferedImage {
    val totp = "otpauth://totp/thomas.felix%40tfelix.de?secret=1234567&issuer=bestia-game.net"
    val barcodeWriter = QRCodeWriter()
    val bitMatrix = barcodeWriter.encode(totp, BarcodeFormat.QR_CODE, 300, 300)

    return MatrixToImageWriter.toBufferedImage(bitMatrix);
  }
}