/**
 * @license Copyright (c) 2003-2013, CKSource - Frederico Knabben. All rights reserved.
 * For licensing, see LICENSE.md or http://ckeditor.com/license
 */

CKEDITOR.editorConfig = function( config ) {
	// Define changes to default configuration here.
	// For the complete reference:
	// http://docs.ckeditor.com/#!/api/CKEDITOR.config
	
	//config.toolbar = 'Full';
	
	if( config.customtoolbar ) 
	{
		config.toolbar = config.customtoolbar;
	}
	else
	{
		config.toolbar =
		[
		 	{ name: 'save', items : [ 'savebtn']},
			{ name: 'document', items : [ 'Sourcedialog','-','Save','NewPage','DocProps','Preview','Print','Templates' ] },
			{ name: 'styles', items : [ 'Format' ] },
			//{ name: 'styles', items : [ 'Styles','Format','Font','FontSize' ] },
			{ name: 'basicstyles', items : [ 'Bold','Italic','Underline' ] },
			{ name: 'links', items : [ 'Link','Unlink' ] },
			{ name: 'paragraph', items : [ 'NumberedList','BulletedList','Outdent','Indent',
				'JustifyLeft','JustifyCenter','JustifyRight','JustifyBlock' ] },
			{ name: 'colors', items : [ 'TextColor','BGColor','RemoveFormat' ] }
			,
			{ name: 'images', items: 		['Image','File','entermedia','Table','HorizontalRule','Smiley','SpecialChar','PageBreak']}
		];
	}
	
	 
	
	/*
	// The toolbar groups arrangement, optimized for two toolbar rows.
	config.toolbarGroups = [
		{ name: 'clipboard',   groups: [ 'clipboard', 'undo' ] },
		{ name: 'editing',     groups: [ 'find', 'selection', 'spellchecker' ] },
		{ name: 'links' },
		{ name: 'insert' },
		{ name: 'forms' },
		{ name: 'tools' },
		{ name: 'document',	   groups: [ 'mode', 'document', 'doctools' ] },
		{ name: 'others' },
		'/',
		{ name: 'basicstyles', groups: [ 'basicstyles', 'cleanup' ] },
		{ name: 'paragraph',   groups: [ 'list', 'indent', 'blocks', 'align', 'bidi' ] },
		{ name: 'styles' },
		{ name: 'colors' , items: [ 'TextColor', 'BGColor' ] },
		{ name: 'tools', groups: [ 'Maximize', 'ShowBlocks' ] },
		{ name: 'about' }
	];
	*/

	// Remove some buttons, provided by the standard plugins, which we don't
	// need to have in the Standard(s) toolbar.
	//config.removeButtons = 'Underline,Subscript,Superscript';

	// Se the most common block elements.
	//config.format_tags = 'p;h1;h2;h3;pre';

	// Make dialogs simpler.
	//config.removeDialogTabs = 'image:advanced;link:advanced';
		
	config.allowedContent = true; 
	
	//config.extraPlugins = 'ajaxsave';//savebtn is the plugin's name
	config.extraPlugins='savebtn,closebtn,colorbutton,panelbutton,justify';		 
	
	config.baseFloatZIndex = 30000
	
	
};
