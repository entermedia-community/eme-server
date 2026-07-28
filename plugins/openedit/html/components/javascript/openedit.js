jQuery(document).ready(function () {
	//insert a chunk of html
	var body = jQuery("body");
	var hide = body.data("hidetoolbar");
	var isBlockfind = $("#application").hasClass("blockfind");
	//console.log(hide);

	if (hide != true && !isBlockfind) {
		var path = window.location.pathname;
		if (window.location.search) {
			path = path + window.location.search;
		}

		jQuery.get(
			"/openedit/components/toolbar/admintoolbarselector.html",
			{ path: path },
			function (data) {
				body.prepend(data);

				loadToolbar();
			},
		);

		$(document).on("click", ".oe-enableedit", function () {
			var apphome = jQuery("#application").data("apphome");
			//<li><a href="$home/openedit/views/workflow/mode/viewdebug.html?origURL=$origURL" ><img src="$home/openedit/theme/images/toolbar/modepreview.gif" border="0" title="Debug Mode" />Debug Mode</a></li>

			var args = $(this).data();
			//Enable dashes on
			$.get(href, args, function (data) {
				//reload page
			});
		});

		$(document).on("click", ".oeDialog", function () {
			var target = $(this).data("target");
			if (target == null) {
				target = $(this).attr("href");
			}
			var title = $(this).data("title");
			if (title != null) {
				$("#modal-title").text(title);
			}
			$("#edit-modal-body").load(target, function (result) {
				var textareas = jQuery(".htmleditor");
				if (textareas.length > 0) {
					loadEditors();
				}
				$("#editmodal").modal("show");
			});
			return false;
		});

		$(document).on("click", ".oemodechange", function (e) {
			e.preventDefault();
			var target = $(this).attr("href");
			var title = $(this).attr("title");
			jQuery.get(target, function () {
				if (title != "DebugMode") {
					window.location.reload();
				}
			});
		});

		jQuery("a.openeditdialog").each(function () {
			var height = jQuery(window).height();
			var width = jQuery(window).width();
			height = height * 0.9;
			width = width * 0.9;
			if (width < 900) {
				width = 1050;
			}

			var newfancy = jQuery(this).fancybox({
				zoomSpeedIn: 300,
				zoomSpeedOut: 300,
				overlayShow: true,
				enableEscapeButton: true,
				type: "iframe",
				height: height,
				width: width,
			});
		});

		//OLD Approach
		jQuery("a.oeinlineedit").on("click", function (e) {
			e.preventDefault();
			e.stopPropagation();

			var parent = $(this).closest(".openeditinline");
			var container = parent.find(".openediteditcontent");

			var editpath = parent.data("editpath");

			// var catalogid = app.data("catalogid");
			//alert("using " + catalogid);
			var home = $("#openedit").data("home");
			if (!home) {
				home = "";
			}
			var savepath = home + "/openedit/components/html/save.html";

			container.data("savepath", savepath);
			container.data("editpath", editpath);

			$(window).trigger("inlinehtmlstart", [container]);

			/*
			CKEDITOR.config.saveSubmitURL = savepath + "?editPath=" + editpath; //TODO: Save this URL specific to this editor
			CKEDITOR.config.filebrowserBrowseUrl =
				home + "/openedit/components/html/browse/index.html?editPath=$editPath";
			CKEDITOR.config.filebrowserUploadUrl =
				home + "/openedit/components/html/edit/actions/imageupload-finish.html";
			CKEDITOR.config.filebrowserImageBrowseUrl =
				home + "/openedit/components/html/browse/index.html?editPath=$editPath";
			CKEDITOR.config.filebrowserImageUploadUrl =
				home + "/openedit/components/html/edit/actions/imageupload-finish.html";
			CKEDITOR.config.entities = false;
			CKEDITOR.config.basicEntities = true;
			var content = container.find(".openediteditcontent").get(0);
			//var content = jQuery(".openediteditcontent" ).get(0);
			content.setAttribute("contenteditable", "true");
			var editor = CKEDITOR.inline(content, {
				extraConfig: { oldcontent: "null" },
				startupFocus: true,
				on: {
					dataReady: function (event) {
						event.editor.config.extraConfig.oldcontent = event.editor.getData();
					},
					blur: function (event) {
						var enCollator = new Intl.Collator("en");
						var data = event.editor.getData();

						if (
							enCollator.compare(data, editor.config.extraConfig.oldcontent) !=
							0
						) {
							$(window).on("beforeunload", function () {
								return "You have unsaved changes.  Reloading will loose these changes.";
							});
						}
						return false;
					},
					savecontentdone: function (event) {
						location.reload();
					},
				},
			});
			*/
		});
	}

	var content = $("textarea.oeeditorhtml");
	if (content.length) {
		$(window).trigger("edithtmlstart", [content]);
	}

	loadHtmlEditor = function (field, viewtype, container) {
		var apphome = jQuery("#application").data("apphome");

		var home = $("#openedit").data("home");
		if (!home) {
			home = "";
		}

		if (viewtype == "html") {
			var savepath = home + apphome + "/components/data/save.html";
			container.data("savepath", savepath);
			var dataid = container.data("dataid");
			if (dataid) {
				container.data("id", dataid);
			}
			$(window).trigger("edithtmlstart", [container]);
		} else if (viewtype == "input") {
			var savepath = home + apphome + "/components/data/save.html";
			var oldborder = container.css("border");
			container.css("border", "1px dashed black");
			content.setAttribute("contenteditable", "true");
			container.focus();
			var options = container.data();
			options.save = true;
			options.oemaxlevel = 1;
			options.id = options.dataid;
			var field = options.field;
			container.keyup(function (evt) {
				//save as we go?
				// enter pressed
				var contents = compoent.innerHTML;
				options[field + ".value"] = contents;

				//TODO: use "post" method for larger inputs not "get"
				$.get(savepath, options, function () {
					//reset border
					//content.setAttribute('contenteditable', 'false');
					container.css("border", oldborder);
				});
			});
			//Capture the enter key
		}
	};

	//onload

	var editmode = jQuery("#application").data("editmode");

	if (editmode == "postedit") {
		var container = $(".oe-editable");
		$(window).trigger("edithtmlstart", [container]);
	}

	//THis is a click that enabled something else to edit. Like a pencil icon
	jQuery(document).on("click", ".oe-dataedit", function (e) {
		console.log("Opening editor");
		var container = $(this).data("target");
		container = $(container);
		// var content = container.get(0);
		// var searchtype = container.data("searchtype");
		// var id = container.data("dataid");
		var field = container.data("field");
		var viewtype = container.data("viewtype");
		if (!viewtype) {
			viewtype = "html";
		}
		var home = $("#openedit").data("home");
		if (!home) {
			home = "";
		}
		e.preventDefault();

		loadHtmlEditor(field, viewtype, container);

		return false;
	});

	var htminp = jQuery(".oehtmlinput");
	if (htminp.length > 0) {
		var field = htminp.data("field");
		var viewtype = "html";
		loadHtmlEditor(field, viewtype, htminp);
	}

	jQuery("form.oeajaxform").on("submit", function () {
		var targetdiv = jQuery(this).attr("targetdiv");
		targetdiv = targetdiv.replace(/\//g, "\\/");
		//allows for posting to a div in the parent from a fancybox.
		if (targetdiv.indexOf("parent.") == 0) {
			targetdiv = targetdiv.substr(7);
			parent.jQuery(this).ajaxSubmit({
				target: "#" + targetdiv,
				success: function () {
					$(document).trigger("domchanged", "#" + targetdiv);
				},
			});

			//closes the fancybox after submitting
			parent.jQuery.fn.fancybox.close();
		} else {
			jQuery(this).ajaxSubmit({
				target: "#" + targetdiv,
				success: function () {
					$(document).trigger("domchanged", "#" + targetdiv);
				},
			});
		}
		return false;
	});
});

loadToolbar = function () {
	jQuery("#oeselector").on("mouseenter", function () {
		if (jQuery("#oeadmintoolbarlocation").is(":visible")) {
			return;
		}
		var me = jQuery(this);
		jQuery.get(me.attr("href"), {}, function (data) {
			me.html(data);
			jQuery("#oeadmintoolbarlocation").on("mouseleave", function () {
				jQuery(this).hide();
			});
		});
	});
};

showHover = function (inAssetId) {
	var el = document.getElementById("oehover");
	el = jQuery(el);
	if (el.attr("status") == "show") {
		if (inAssetId == el.attr("assetid")) {
			el.show();
		}
	}
};

refreshFileMenu = function () {
	var editpath = $("#fileoptionsmenu").data("editpath");

	var home = $("#openedit").data("home");
	if (!home) {
		home = "";
	}
	$("#fileoptionsmenu").load(
		home +
			"/openedit/components/html/edit/menu.html?oemaxlevel=1&editPath=" +
			editpath,
	);
};
