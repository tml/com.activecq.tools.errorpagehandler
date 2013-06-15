;$(function() {
    $('#error-page-handler .error-page-handler-toggle').click(function() {
        $this = $(this);
        $section = $this.closest('.error-page-handler-section');

        if($section.hasClass('error-page-handler-collapsed')) {
            $section.removeClass('error-page-handler-collapsed');
            $section.addClass('error-page-handler-expanded')
            $this.text($this.data('collapse-text'));
        } else {
            $section.removeClass('error-page-handler-expanded')
            $section.addClass('error-page-handler-collapsed');
            $this.text($this.data('expand-text'));
        }
    });
});

