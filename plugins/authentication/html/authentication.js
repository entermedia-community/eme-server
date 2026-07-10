$(document).ready(function () {
	const otpInputs = () => $(".otp");

	lQuery(".otp").livequery("input", function () {
		const $inputs = otpInputs();
		const idx = $inputs.index(this);
		const val = $(this).val().replace(/\D/g, "");
		$(this).val(val);
		if (val && idx < $inputs.length - 1) $inputs.eq(idx + 1).focus();
		checkOtpFilled();
	});

	lQuery(".otp").livequery("keydown", function (e) {
		const $inputs = otpInputs();
		const idx = $inputs.index(this);
		if (e.key === "Backspace" && !$(this).val() && idx > 0)
			$inputs.eq(idx - 1).focus();
	});

	lQuery(".otp").livequery("paste", function (e) {
		const text = (e.originalEvent.clipboardData || window.clipboardData)
			.getData("text")
			.replace(/\D/g, "")
			.slice(0, 6);
		if (!text) return;
		e.preventDefault();
		const $inputs = otpInputs();
		text.split("").forEach((ch, i) => {
			$inputs.eq(i).val(ch);
		});
		$inputs.eq(Math.min(text.length, $inputs.length - 1)).focus();
		checkOtpFilled();
	});

	function checkOtpFilled() {
		const $inputs = otpInputs();
		const filled = $inputs.toArray().every((i) => $(i).val().length === 1);
		$("#loginBtn").prop("disabled", !filled);

		var codes = "";
		$(".otp").each(function () {
			codes += $(this).val();
		});
		$("#loginCode").val(codes);
	}
});
