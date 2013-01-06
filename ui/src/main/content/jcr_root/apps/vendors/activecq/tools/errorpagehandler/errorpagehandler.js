;$CQ(function() {
    $CQ('#acq-eph .acq-eph-toggle').click(function() {
        $this = $CQ(this);
        $section = $this.closest('.acq-eph-section');

        if($section.hasClass('acq-eph-collapsed')) {
            $section.removeClass('acq-eph-collapsed');
            $section.addClass('acq-eph-expanded')
            $this.text($this.data('collapse-text'));
        } else {
            $section.removeClass('acq-eph-expanded')
            $section.addClass('acq-eph-collapsed');
            $this.text($this.data('expand-text'));
        }
    });
});

