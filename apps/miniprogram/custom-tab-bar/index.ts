Component({
  data: {
    selected: 0,
    color: "#475569",
    selectedColor: "#1a4fa0",
    list: [
      {
        pagePath: "/pages/index/index",
        text: "首页",
        iconPath: "/images/tab-home.png",
        selectedIconPath: "/images/tab-home-active.png"
      },
      {
        pagePath: "/pages/training/index",
        text: "训练",
        iconPath: "/images/tab-training.png",
        selectedIconPath: "/images/tab-training-active.png"
      },
      {
        pagePath: "/pages/records/index",
        text: "记录",
        iconPath: "/images/tab-records.png",
        selectedIconPath: "/images/tab-records-active.png"
      },
      {
        pagePath: "/pages/alerts/index",
        text: "预警",
        iconPath: "/images/tab-alerts.png",
        selectedIconPath: "/images/tab-alerts-active.png"
      }
    ]
  },
  lifetimes: {
    attached() {
      this.updateSelected();
    }
  },
  pageLifetimes: {
    show() {
      this.updateSelected();
    }
  },
  methods: {
    updateSelected() {
      const pages = getCurrentPages();
      if (pages.length > 0) {
        const currentPage = pages[pages.length - 1];
        const route = "/" + currentPage.route;
        const index = this.data.list.findIndex((item: { pagePath: string }) => item.pagePath === route);
        if (index !== -1 && index !== this.data.selected) {
          this.setData({ selected: index });
        }
      }
    },
    switchTab(e: WechatMiniprogram.CustomEvent) {
      const data = e.currentTarget.dataset;
      const url = data.path;
      wx.switchTab({ url });
      this.setData({ selected: data.index });
    }
  }
});
