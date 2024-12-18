function spa() {
	return {
		title: '',
        currentRoute: '/',
        currentPage: '',

        // Initialize the SPA
        init() {
            const loggedIn = this.isLoggedIn();
            if (!loggedIn) {
                window.location.href = 'login.html';
            } else {
                window.addEventListener('hashchange', () => this.loadRoute());
            }
        },

        isLoggedIn() {
            return sessionStorage.getItem('username') !== null;
        },

        // Load the route based on the hash
        loadRoute() {
            this.currentRoute = window.location.hash.slice(1) || '/';
            this.loadPage(this.currentRoute);
            this.setTitle(this.currentRoute);
        },

        // Dynamically load page content
        async loadPage(route) {
            const routes = {
                '/': 'pages/index.html',
                '/login': 'login.html',
                '/subscription': 'pages/subscription.html',
                '/subscriptions': 'pages/subscriptions.html',
                '/apis': 'pages/apis.html',
                '/analytics': 'pages/analytics.html'
            };

            const page = routes[route];
            if (page) {
                try {
                    this.currentPage = fetchPage(page);
                } catch (error) {
                    console.log(`error occured: ${error}`);
                    this.currentPage = fetchPage('404.html');
                }
            } else {
                console.log(`page not found: ${page}`);
                this.currentPage = fetchPage('404.html');
            }
        },

        setTitle(route) {
            const titles = {
                '/': '',
                '/subscription': 'My Subscription',
                '/subscriptions': 'Subscriptions',
                '/apis': 'APIs',
                '/analytics': 'Analytics'
            };
            this.title = titles[route];
        },

        logout() {
            sessionStorage.clear();
            this.init();
        }
    };
}

async function fetchPage(page) {
    let res = await fetch(page);
    if (!res.ok) {
        res = await fetch('404.html');
        return await res.text();
    }
    return await res.text();
}

function fetchData(url) {
    return {
	    isLoading: false,
		data: null,
		postData: null,
		errors: null,

		get() {
		    this.isLoading = true;
		    const options = { headers: {'Content-Type': 'application/json'}, credentials: 'include' };
			fetch(url, options)
				.then(res => res.json())
				.then(data => {
					this.isLoading = false;
					this.data = data;
				});
		},

		post(payload) {
		    const options = { headers: {'Content-Type': 'application/json'}, credentials: 'include', method: 'post', body: JSON.stringify(payload) };
			fetch(url, options)
                .then(res => res.json())
                .then(res => {
                    if (res.ok) {
                        this.postData = res;
                    } else {
                        this.errors = res;
                    }
                });
		}
	}
}

function authBasic() {
	return {
		username: null,
		password: null,

		login() {
			const options = { headers: {'Content-Type': 'application/json', 'Authorization': 'Basic ' + boa(`${username}:${password}`)}, method: 'post' };
            fetch('/apim/auth/token/web', options)
                .then(res => {
                    if (res.ok)
                      return res.json();
                    else
                      throw new Error("Authentication failed: " + res);
                })
                .then(data => {
                    sessionStorage.setItem("username", data.username);
                    sessionStorage.setItem("roles", data.roles);
                    window.location.href = "pages/index.html";
                })
                .catch((err) => console.log(err));
		}
	}
}