const input = document.getElementById("qrInput");
const canvas = document.getElementById("canvas");
const ctx = canvas.getContext("2d");
const result = document.getElementById("result");

input.addEventListener("change", () => {
  const file = input.files[0];
  if (!file) return;

  const img = new Image();
  img.onload = () => {
    canvas.width = img.width;
    canvas.height = img.height;
    ctx.drawImage(img, 0, 0);

    const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
    const qrCode = jsQR(imageData.data, imageData.width, imageData.height);

    if (qrCode) {
      result.textContent = "QR Data: " + qrCode.data;
    } else {
      result.textContent = "No QR code detected.";
    }
  };

  img.src = URL.createObjectURL(file);
});
