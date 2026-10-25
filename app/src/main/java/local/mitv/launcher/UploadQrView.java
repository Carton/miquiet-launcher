package local.mitv.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import io.nayuki.qrcodegen.QrCode;

/** Local QR rendering with integer module sizes and the required four-module quiet zone. */
final class UploadQrView extends View {
    private final QrCode qr;
    private final Paint ink = new Paint();
    UploadQrView(Context context,String address) {
        super(context);
        qr=QrCode.encodeText(address,QrCode.Ecc.MEDIUM);
        ink.setColor(Color.BLACK);ink.setAntiAlias(false);
        setBackgroundColor(Color.WHITE);
        setContentDescription("扫码打开照片上传页，连接码已包含");
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int scale=Math.min(getWidth(),getHeight())/(qr.size+8);
        if(scale<1)return;
        int x=(getWidth()-qr.size*scale)/2,y=(getHeight()-qr.size*scale)/2;
        for(int row=0;row<qr.size;row++)for(int column=0;column<qr.size;column++) {
            if(qr.getModule(column,row))canvas.drawRect(x+column*scale,y+row*scale,
                x+(column+1)*scale,y+(row+1)*scale,ink);
        }
    }
}
